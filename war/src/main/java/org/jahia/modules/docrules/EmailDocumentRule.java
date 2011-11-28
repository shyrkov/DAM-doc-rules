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

import java.io.StringWriter;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.usermanager.JahiaGroup;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.utils.ScriptEngineUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document rule that sends a notification e-mail to specified group of users.
 * 
 * @author Sergiy Shyrkov
 */
public class EmailDocumentRule implements DocumentRule {

    private static final Logger logger = LoggerFactory.getLogger(EmailDocumentRule.class);

    private String body;

    private JahiaGroupManagerService groupManagerService;

    private MailService mailService;

    private String subject;

    private String evaluate(String subject, JCRNodeWrapper document) {
        if (subject.contains("${")) {
            try {
                ScriptEngine byName = ScriptEngineUtils.getInstance().getEngineByName("velocity");
                ScriptContext scriptContext = byName.getContext();
                final Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("document", document);
                scriptContext.setWriter(new StringWriter());
                scriptContext.setErrorWriter(new StringWriter());
                byName.eval(subject, bindings);
                return scriptContext.getWriter().toString().trim();
            } catch (ScriptException e) {
                logger.error("Error while evaluating value [" + subject + "]", e);
            }
        }

        return null;
    }

    public void execute(JCRNodeWrapper document) throws RepositoryException {
        JCRNodeWrapper folder = document.getParent();
        List<String> receivers = getReceivers(document, folder);

        if (receivers.isEmpty()) {
            logger.info("No receivers are found for the e-mail notification of the document rule."
                    + " Skip executing rules on node {}", document.getPath());
            return;
        }

        String subject = getSubject(document, folder);
        String body = getBody(document, folder);

        mailService.sendHtmlMessage(null, StringUtils.join(receivers, ","), null, null, subject,
                body);
    }

    private String getBody(JCRNodeWrapper document, JCRNodeWrapper folder)
            throws ValueFormatException, PathNotFoundException, RepositoryException {
        String body = null;
        if (folder.hasProperty("j:documentRuleBody")) {
            body = folder.getProperty("j:documentRuleBody").getString();
        }
        body = StringUtils.defaultIfBlank(body, this.body);

        return evaluate(body, document);
    }

    private List<String> getReceivers(JCRNodeWrapper document, JCRNodeWrapper folder)
            throws RepositoryException {
        JahiaGroup group = null;

        if (folder.hasProperty("j:documentRuleTo")) {
            JCRNodeWrapper groupNode = (JCRNodeWrapper) folder.getProperty("j:documentRuleTo")
                    .getNode();
            if (groupNode != null) {
                JCRSiteNode resolveSite = groupNode.getResolveSite();
                group = groupManagerService.lookupGroup(resolveSite != null ? resolveSite.getID()
                        : 0, groupNode.getName());
            }
        }

        if (group == null) {
            logger.info("No target group specified for the document rule on the parent folder {}."
                    + " Skip executing rules on node {}", folder.getPath(), document.getPath());
            return Collections.emptyList();
        }

        List<String> receivers = new LinkedList<String>();

        Collection<Principal> members = group.getMembers();
        for (Principal principal : members) {
            if (!(principal instanceof JahiaUser)) {
                continue;
            }

            JahiaUser user = (JahiaUser) principal;
            String email = !UserPreferencesHelper.areEmailNotificationsDisabled(user) ? UserPreferencesHelper
                    .getPersonalizedEmailAddress(user) : null;

            if (StringUtils.isNotEmpty(email)) {
                receivers.add(email);
            }
        }

        return receivers;
    }

    private String getSubject(JCRNodeWrapper document, JCRNodeWrapper folder)
            throws ValueFormatException, PathNotFoundException, RepositoryException {
        String subject = null;
        if (folder.hasProperty("j:documentRuleSubject")) {
            subject = folder.getProperty("j:documentRuleSubject").getString();
        }
        subject = StringUtils.defaultIfBlank(subject, this.subject);

        return evaluate(subject, document);
    }

    public boolean isApplicable(JCRNodeWrapper document) {
        return mailService.isEnabled();
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setGroupManagerService(JahiaGroupManagerService groupManagerService) {
        this.groupManagerService = groupManagerService;
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
