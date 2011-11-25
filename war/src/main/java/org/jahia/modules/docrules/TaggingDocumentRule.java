/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group SA. All rights reserved.
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

import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.tags.TaggingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document rule that does the tagging of the document with provided tags.
 * 
 * @author Sergiy Shyrkov
 */
public class TaggingDocumentRule implements DocumentRule {

    private static final Logger logger = LoggerFactory.getLogger(TaggingDocumentRule.class);

    private TaggingService taggingService;

    public void execute(JCRNodeWrapper document) throws RepositoryException {
        JCRNodeWrapper folder = document.getParent();
        String[] tags = null;
        if (folder.hasProperty("j:documentRuleTags")) {
            tags = StringUtils.split(folder.getProperty("j:documentRuleTags").getString(), " ,;");
        }

        if (tags == null || tags.length == 0) {
            logger.info("No tags specoifed for the document rule on the parent folder {}."
                    + " Skip executing rules on node {}", folder.getPath(), document.getPath());
            return;
        }

        // workaround for http://jira.jahia.org/browse/QA-3179
        if (!document.isNodeType("jmix:tagged")) {
            document.addMixin("jmix:tagged");
        }

        JCRSiteNode resolveSite = document.getResolveSite();

        taggingService.tag(document.getPath(), StringUtils.join(tags, ","),
                resolveSite != null ? resolveSite.getName() : "systemsite", true,
                document.getSession());

    }

    public boolean isApplicable(JCRNodeWrapper document) {
        return true;
    }

    public void setTaggingService(TaggingService taggingService) {
        this.taggingService = taggingService;
    }

}
