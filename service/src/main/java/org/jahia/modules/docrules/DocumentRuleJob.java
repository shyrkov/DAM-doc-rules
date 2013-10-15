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

import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Background task for creating PDF versions of the documents.
 * 
 * @author Sergiy Shyrkov
 */
public class DocumentRuleJob extends BackgroundJob {

    public static final String JOB_RULE_BEAN_ID = "ruleBeanId";

    public static final String JOB_UUID = "uuid";

    public static final String JOB_WORKSPACE = "workspace";

    private static final Logger logger = LoggerFactory.getLogger(DocumentRuleJob.class);

    /**
     * Does the execution of the rule on the provided document node
     * 
     * @param documentNode
     *            the document node to execute the rule on
     * @param beanId
     *            the rule bean ID
     * @throws Exception
     *             in case of a rule error
     */
    public static void executeRule(JCRNodeWrapper documentNode, String beanId) throws Exception {
        DocumentRule rule = null;
        try {
            rule = (DocumentRule) SpringContextSingleton.getModuleBean(beanId);
        } catch (NoSuchBeanDefinitionException e) {
            // rule bean not found
            logger.warn("Unable to lookup Spring bean with ID {}."
                    + " Skip execution of rule on document node {}", beanId, documentNode.getPath());
            return;
        }

        if (rule.isApplicable(documentNode)) {
            rule.execute(documentNode);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Rule {} is not applicable on document {}. Skipping", beanId,
                        documentNode.getPath());
            }
        }
    }

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {
        JobDataMap data = jobExecutionContext.getJobDetail().getJobDataMap();
        final String ruleBeanId = (String) data.get(JOB_RULE_BEAN_ID);
        final String uuid = (String) data.get(JOB_UUID);
        String workspace = StringUtils.defaultIfEmpty((String) data.get(JOB_WORKSPACE),
                Constants.EDIT_WORKSPACE);
        JCRTemplate.getInstance().doExecuteWithSystemSession(null, workspace,
                new JCRCallback<Boolean>() {
                    public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {

                        JCRNodeWrapper node = session.getNodeByIdentifier(uuid);
                        try {
                            executeRule(node, ruleBeanId);
                            session.save();
                        } catch (Exception e) {
                            logger.error("Error executing rule " + ruleBeanId
                                    + " on the document node " + node.getPath(), e);
                        }

                        return Boolean.TRUE;
                    }
                });
    }

}
