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

package org.jahia.modules.docrules.rules;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.drools.spi.KnowledgeHelper;
import org.jahia.modules.docrules.DocumentRuleJob;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for executing defined document rules from the right-hand-side (consequences) of rules.
 * 
 * @author Sergiy Shyrkov
 */
public class DocumentRulesRuleService {

    private static Logger logger = LoggerFactory.getLogger(DocumentRulesRuleService.class);

    private SchedulerService schedulerService;

    /**
     * Executes the document rules, defined on the parent folder, on the provided node.
     * 
     * @param nodeFact
     *            the node to execute rules on
     * @param drools
     *            the rule engine helper class
     * @throws RepositoryException
     *             in case of an error
     */
    public void executeRules(AddedNodeFact nodeFact, KnowledgeHelper drools)
            throws RepositoryException {
        JCRNodeWrapper doc = nodeFact.getNode();
        JCRNodeWrapper folder = doc.getParent();
        Set<String> mixins = new HashSet<String>();
        if (folder.isNodeType("jmix:applyDocumentRules")) {
            ExtendedNodeType[] mixinNodeTypes = folder.getMixinNodeTypes();
            for (ExtendedNodeType mixin : mixinNodeTypes) {
                if (mixin.isNodeType("jmix:applyDocumentRules")
                        && !mixin.getName().equals("jmix:applyDocumentRules")) {
                    mixins.add(StringUtils.substringAfter(mixin.getName(), ":"));
                }
            }
        }

        String path = doc.getPath();
        if (mixins.isEmpty()) {
            logger.info("No document rules are defined on the parent folder {}."
                    + " Skip executing rules on node {}", folder.getPath(), path);
            return;
        }

        for (String ruleBeanId : mixins) {
            try {
                long timer = System.currentTimeMillis();

                String backgroundJobProperty = "j:" + ruleBeanId + "AsBackgroundJob";
                if (folder.hasProperty(backgroundJobProperty)
                        && folder.getProperty(backgroundJobProperty).getBoolean()) {
                    // execute as a background job
                    JobDetail jobDetail = BackgroundJob.createJahiaJob("Document rule "
                            + ruleBeanId + " for " + doc.getName(), DocumentRuleJob.class);
                    JobDataMap jobDataMap = jobDetail.getJobDataMap();
                    jobDataMap.put(DocumentRuleJob.JOB_RULE_BEAN_ID, ruleBeanId);
                    jobDataMap.put(DocumentRuleJob.JOB_UUID, doc.getIdentifier());
                    jobDataMap.put(DocumentRuleJob.JOB_WORKSPACE, doc.getSession().getWorkspace()
                            .getName());

                    schedulerService.scheduleJobNow(jobDetail);

                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Document rule '{}' scheduled as a backgound job on node {} in {} ms",
                                new Object[] { ruleBeanId, path,
                                        (System.currentTimeMillis() - timer) });
                    }
                } else {
                    DocumentRuleJob.executeRule(doc, ruleBeanId);

                    if (logger.isDebugEnabled()) {
                        logger.debug("Document rule '{}' executed on node {} in {} ms",
                                new Object[] { ruleBeanId, path,
                                        (System.currentTimeMillis() - timer) });
                    }
                }

            } catch (Exception e) {
                logger.warn("Error executing document rule '" + ruleBeanId + "' on node " + path, e);
            }
        }
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

}