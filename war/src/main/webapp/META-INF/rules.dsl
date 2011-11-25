[condition][]A file has been moved=node : AddedNodeFact ( types contains "nt:file" ) and contentNode : AddedNodeFact () from node.content and not ChangedPropertyFact ( name=="jcr:data" , node==contentNode )
[condition][]- the parent has document rules defined=node.parent.types contains "jmix:applyDocumentRules"
[consequence][]Execute document rules on {node}=documentRulesService.executeRules({node}, drools);
