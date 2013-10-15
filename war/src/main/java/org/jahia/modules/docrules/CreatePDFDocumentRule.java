/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2013 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.docrules;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.artofsolving.jodconverter.office.OfficeException;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.transform.DocumentConverterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document rule that does the conversion of the document to a PDF file.
 * 
 * @author Sergiy Shyrkov
 */
public class CreatePDFDocumentRule implements DocumentRule {

    private static final Logger logger = LoggerFactory.getLogger(CreatePDFDocumentRule.class);

    private boolean overwriteIfExists = true;

    private static JCRNodeWrapper getTargetFolder(JCRNodeWrapper document)
            throws ValueFormatException, PathNotFoundException, RepositoryException {
        JCRNodeWrapper folder = document.getParent();

        if (folder.hasProperty("j:documentRuleSubfolder")) {
            String subfolder = folder.getProperty("j:documentRuleSubfolder").getString();
            if (subfolder != null && subfolder.length() > 0) {
                folder.getSession().checkout(folder);
                folder = folder.hasNode(subfolder) ? folder.getNode(subfolder) : folder.addNode(
                        subfolder, "jnt:folder");
            }
        }

        return folder;
    }

    /**
     * Checks if the specified mime type belongs to one of the specified groups (like pdf,word,openoffice, etc.).
     * 
     * @param mimeType
     *            the mime type to be checked
     * @param mimeTypeGroups
     *            the groups the specified mime type should belong to
     * @return if the specified mime type belongs to one of the specified groups (like pdf,word,openoffice, etc.)
     */
    public static boolean isMimeTypeGroup(String mimeType, String... mimeTypeGroups) {
        if (mimeType == null) {
            return false;
        }

        boolean found = false;
        for (String grp : mimeTypeGroups) {
            List<String> mimeTypes = null;
            if (grp.contains("/")) {
                mimeTypes = new LinkedList<String>();
                mimeTypes.add(grp);
            } else {
                mimeTypes = JCRContentUtils.getInstance().getMimeTypes().get(grp);
            }

            if (mimeTypes == null) {
                continue;
            }
            for (String mime : mimeTypes) {
                if (mime.contains("*")) {
                    found = Pattern.matches(
                            StringUtils.replace(StringUtils.replace(mime, ".", "\\."), "*", ".*"),
                            mimeType);
                } else {
                    found = mime.equals(mimeType);
                }
                if (found) {
                    break;
                }
            }
            if (found) {
                break;
            }
        }

        return found;
    }

    private DocumentConverterService documentConverterService;

    private String[] supportedDocumentFormats;

    public void execute(JCRNodeWrapper document) throws OfficeException, IOException,
            UnsupportedRepositoryOperationException, LockException, RepositoryException {
        File inFile = null;
        File outFile = null;
        try {
            inFile = File.createTempFile("doc-rules", null);
            JCRContentUtils.downloadFileContent(document, inFile);
            outFile = documentConverterService.convert(inFile, document.getFileContent()
                    .getContentType(), "application/pdf");
            if (outFile != null) {
                JCRNodeWrapper folder = getTargetFolder(document);
                folder.getSession().checkout(folder);
                String newName = StringUtils.substringBeforeLast(document.getName(), ".") + ".pdf";
                if (overwriteIfExists) {
                    if (folder.hasNode(newName)) {
                        folder.getNode(newName).remove();
                    }
                } else {
                    newName = JCRContentUtils.findAvailableNodeName(folder, newName);
                }
                BufferedInputStream convertedStream = null;
                try {
                    convertedStream = new BufferedInputStream(new FileInputStream(outFile));
                    JCRNodeWrapper pdf = folder.uploadFile(newName, convertedStream,
                            "application/pdf");
                    if (logger.isDebugEnabled()) {
                        logger.debug("Converted document {} to PDF document at {}",
                                document.getPath(), pdf.getPath());
                    }
                } finally {
                    IOUtils.closeQuietly(convertedStream);
                }
            }
        } finally {
            FileUtils.deleteQuietly(inFile);
            FileUtils.deleteQuietly(outFile);
        }
    }

    public boolean isApplicable(JCRNodeWrapper document) {
        if (!documentConverterService.isEnabled()) {
            logger.warn("Document converter service is not enabled."
                    + " Skip converting document node {}", document.getPath());
            return false;
        }

        String mimeType = document.getFileContent().getContentType();

        if (null == mimeType) {
            logger.warn("Document has no MIME type defined." + " Skip converting document node {}",
                    document.getPath());
            return false;
        }

        return isMimeTypeGroup(mimeType, supportedDocumentFormats);
    }

    public void setDocumentConverterService(DocumentConverterService converterService) {
        this.documentConverterService = converterService;
    }

    public void setSupportedDocumentFormats(String[] supportedDocumentFormats) {
        this.supportedDocumentFormats = supportedDocumentFormats != null
                && supportedDocumentFormats.length > 0 ? supportedDocumentFormats : null;
    }

    public void setOverwriteIfExists(boolean overwriteIfExists) {
        this.overwriteIfExists = overwriteIfExists;
    }

}
