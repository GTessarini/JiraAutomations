def statusHTML = ""
def projectKeyToUpdate = ""
def isStatusThatUpdated = false
def statusThatHasToBeToModify= "DONE"

/* Check if is subtask */
if(issue == null || issue.fields["issuetype"]["subtask"] == false){
    return
}

 def relationStatusRevisionSubtask = [
    ["status": "REV 2", "subtask": ["Revision 2"]],
    ["status": "Layout Verified", "subtask": ["Verify Layout"]]
]

def relationStatusProjectKey = [
    ["status": "REV 2", "projectKey": ["KHTML", "KART"]],
    ["status": "Layout Verified", "projectKey": ["KHTML", "KART"]]
]

// Retrieve all the subtasks of this issue's subtask parent
def parentId = issue.fields["parent"]["id"]
def issueParent = get("/rest/api/2/issue/" + parentId + "" )
        .asObject(Map)
        .body

def issueId = issue.id
def issueKey = issue.key
def issueSummary = issue.fields['summary'] as String
def issueStatus = issue.fields['status']['name'] as String
def issueLinkedId = issueParent.fields["issuelinks"]["outwardIssue"]["id"][0]
def subtasks = issueParent["fields"]["subtasks"]

def getHTMLStatusBySubtask(statusHTML, subtasks, relationStatusRevisionSubtask, statusThatHasToBeToModify){
    for(subtask in subtasks){
      def subTaskSummary = subtask["fields"]["summary"]
      def subTaskStatusName = subtask["fields"]["status"]["name"]
      if(subTaskStatusName != null && subTaskStatusName != ""){
        for(statusHTMLSubtask in relationStatusRevisionSubtask){
            for(subtaskInStatus in statusHTMLSubtask["subtask"]){
                if(
                    subTaskSummary.toString().toLowerCase().replaceAll(" ", "") == subtaskInStatus.toString().toLowerCase().replaceAll(" ", "")
                    && subTaskStatusName.toString().toLowerCase().replaceAll(" ", "") == statusThatHasToBeToModify..toString().toLowerCase().replaceAll(" ", "") 
                ){
                    statusHTML = statusHTMLSubtask["status"]
                }
            }
        }
      }
    }
    logger.info("" + statusHTML)
    return statusHTML
}

/* Check if status has updated the issue */
def getChangeLogIssue(issueId, maxResults, resultPosition){
    def changeLog = null;
    if(resultPosition == null){
      changeLog = get('/rest/api/2/issue/' + issueId + '?expand=changelog&maxResults=' + maxResults + '').asObject(Map).body["changelog"]["histories"]
    }else{
      /* Position: 0 is the last chaneg done */
      changeLog = get('/rest/api/2/issue/' + issueId + '?expand=changelog&maxResults=' + maxResults + '').asObject(Map).body["changelog"]["histories"][resultPosition]
    }
    return changeLog
}

def thisUpdate = getChangeLogIssue(issueId, 1, 0)
for(itemUpdated in thisUpdate["items"]){
    if(isStatusThatUpdated == false){
        for(item in itemUpdated){
            if(itemUpdated.field.toString().toLowerCase().trim() == "status"){
                isStatusThatUpdated = true
                break
            }
        }
    }
}


if(isStatusThatUpdated == false){
    logger.info("Was issue status that changed")
    return
}else{
    logger.info("Was not issue status that changed")
}

statusHTML = getHTMLStatusBySubtask(statusHTML, subtasks, relationStatusRevisionSubtask, statusThatHasToBeToModify)

def getIssue(jqlCondition){
    def issueLinked = get('/rest/api/2/search')
            .queryString('jql', jqlCondition)
            .asObject(Map)
            .body["issues"] as List<Map>
    return issueLinked
}

def updateIssueField(issueId, issueProjectKey, issueFieldId, issueFieldName, issueFieldNewValue){
    put("/rest/api/2/issue/" + issueId + "")
        .header("Content-Type", "application/json")
        .body([
            fields:[
                (issueFieldId): issueFieldNewValue
            ]
        ]).asString() 
    logger.info("Updating field: '" + issueFieldName +  "' in linked issue found in project: '" + issueProjectKey + "' with the id: '" + issueId + "' with value: '" + issueFieldNewValue.toString() + "'")

}

def updateKanban(issueId, issueStatus, pkToUpdate, issueLinkedId){
    // get custom fields
    def customFields = get("/rest/api/2/field")
        .asObject(List)
        .body
        .findAll { 1 == 1 } as List<Map>
        
    def fieldStatusHtmlName = 'Revision Status'
    def fieldStatusRevisionId = customFields.find { it.name == fieldStatusHtmlName }?.id
    
    def issueInProjectToUpdate = getIssue('project=' + pkToUpdate + ' AND issuetype=Tarefa AND ( issue in linkedIssues(' + issueLinkedId.toString() + ') )')
    
    if(issueInProjectToUpdate == null || !(issueInProjectToUpdate.size() > 0)){
        if(pkToUpdate == "KPROJ" && issueLinkedId){
            updateIssueField(issueLinkedId, pkToUpdate, fieldStatusRevisionId, fieldStatusHtmlName, issueStatus.toString())
        }else{
            logger.info("Has not an linked issue in project: '" + pkToUpdate + "'")
            return
        }
    }else{
         for(i in issueInProjectToUpdate){
            def issueInProjectToUpdateId = i.id
            logger.info("Linked issue found in project: '" + pkToUpdate + "' with the id: '" + issueInProjectToUpdateId + "'")
            def issueKeyInIssueLinked = i.key
            updateIssueField(issueKeyInIssueLinked, pkToUpdate, fieldStatusRevisionId, fieldStatusHtmlName, issueStatus.toString())
        }
    }
}


for(statusAndProjectKey in relationStatusProjectKey){
    if(statusAndProjectKey.status.toString().toLowerCase().replaceAll(" ", "") == statusHTML.toString().toLowerCase().replaceAll(" ", "")){
        projectKeyToUpdate = statusAndProjectKey.projectKey
        if(!(projectKeyToUpdate instanceof java.util.ArrayList)){
            updateKanban(issueId, statusHTML, projectKeyToUpdate, issueLinkedId)
        }else{
            for(pkToUpdate in projectKeyToUpdate){
                updateKanban(issueId, statusHTML, pkToUpdate, issueLinkedId)
            }
        }
    }
}

/* Gabriel tessarini */
