def isStatusThatUpdated = false
def statusThatUpdatedTo = null

def projectKeyToCreateIssue = ""
def statusTrigger = ""

 def relationStatusProjectKey = [
    ["status": "HTML", "projectKey": ["KHTML"]],
    ["status": "Art", "projectKey": ["KART"]],
    ["status": "Marketing", "projectKey": ["KMKT", "KART"]],
    ["status": "Product Release", "projectKey": ["KPROJECT", "KMANUFACT"]]
]
    
def issueId = issue.id
def issueKey = issue.key
def issueSummary = issue.fields['summary'] as String
def issueStatus = issue.fields['status']['name'] as String

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
    if(itemUpdated.field.toString().toLowerCase().trim() == "status"){
        isStatusThatUpdated = true
        statusThatUpdatedTo = itemUpdated["toString"].toString()
        logger.info("Was status issue that changed")
        break
    }else{
        logger.info("Was not status issue that changed")
        return
    }
}

def getCustomFieldId(fieldName){
     def customFields = get("/rest/api/2/field")
        .asObject(List)
        .body
        .findAll { 1 == 1 } as List<Map>
    def customFieldId = customFields.find { it.name == fieldName }?.id
    return customFieldId
}

def getIssueTypeId(issueTypeName, issueTypeIsSubtask){
    // Retrieve all issue types
    def typeResp = get('/rest/api/2/issuetype').asObject(List)
    //assert typeResp.status == 200
    def issueTypes = typeResp.body as List<Map>
    // Set the type Task required
    def issueTypeId = issueTypes.find { it.name == issueTypeName && it.subtask == issueTypeIsSubtask}?.id
    return  issueTypeId
}

def createIssue(issueSummary, projectKeyToCreateIssue, issueTypeId, issueEpicKeyToCreate) { 
   def fieldEpicLinkId = getCustomFieldId('Epic Link')

   def issueToCreate = [
        fields: [
            summary    : issueSummary, 
            description: "",
            project    : [
                key: projectKeyToCreateIssue
            ],
            issuetype  : [
                id: issueTypeId
            ]
         ]
    ]
    if(
        fieldEpicLinkId != null && fieldEpicLinkId != "" && fieldEpicLinkId != []
        && issueEpicKeyToCreate != null && issueEpicKeyToCreate != "" && issueEpicKeyToCreate != []
    ){
       issueToCreate.fields[fieldEpicLinkId] = issueEpicKeyToCreate.toString()
    }
    def issuePost = post("/rest/api/2/issue")
				.header("Content-Type", "application/json")
				.body(issueToCreate)
				.asObject(Map)
	def issueCreated = issuePost.body
	assert issuePost.status >= 200 && issuePost.status < 300 && issueCreated && issueCreated.key != null
	return issueCreated
}

def linkIssue(issueId, issueCreatedId ) { 
    def issueLinkCreated = post('/rest/api/2/issueLink')
        .header('Content-Type', 'application/json')
        .body(
            [
                type: [ name: "Relates" ],
                outwardIssue: [ id: issueId ],
                inwardIssue: [ id: issueCreatedId ] 
            ]
        )
        .asString()
    assert issueLinkCreated.status == 201
    return issueLinkCreated
}

def getIssue(jqlCondition){
    def issuesInProjectToCreate = get('/rest/api/2/search')
            .queryString('jql', jqlCondition)
            .asObject(Map)
            .body["issues"] as List<Map>
    return issuesInProjectToCreate
}

def getIssueEpicSummaryByKey(projectKey, issueKey){
    def issueEpic = get('/rest/api/2/search')
            .queryString('jql', 'project=' + projectKey +  ' AND issuetype=Epic AND key=' + issueKey)
            .asObject(Map)
            .body["issues"] as List<Map>
    
    def issueEpicSummary = ""
    if(issueEpic != null && issueEpic != [] && issueEpic != ""){
        issueEpicSummary =  issueEpic[0].fields['summary'].toString()
    }
    return issueEpicSummary
}

def getIssueEpicKeyBySummary(projectKey, issueSummary){
    def issueEpic = get('/rest/api/2/search')
            .queryString('jql', 'project=' + projectKey +  ' AND issuetype=Epic AND summary~' + issueSummary)
            .asObject(Map)
            .body["issues"] as List<Map>
    
    def issueEpicKey = ""
    if(issueEpic != null && issueEpic != [] && issueEpic != ""){
        issueEpicKey =  issueEpic[0].key
    }
    return issueEpicKey
}

def updateKanban(issueId, issueSummary, issueStatus, statusTrigger, pkToCreateIssue){
    if(issueStatus.toString().toLowerCase().replaceAll(" ", "") != statusTrigger.toString().toLowerCase().replaceAll(" ", "")){
        logger.info("Näo é o status para atualizar o(s) projeto(s) Kanban: " + pkToCreateIssue + "")
        return
    }
    /* .queryString('jql', 'project=DES AND issuetype=Task AND ( issue in linkedIssues(' + issueId + ') )') */
    def issuesInProjectToCreate = getIssue('project=' + pkToCreateIssue + ' AND issuetype=Task AND ( issue in linkedIssues(' + issueId + ') )')

    if(!issuesInProjectToCreate || issuesInProjectToCreate == null || issuesInProjectToCreate.size() < 1){
        issuesInProjectToCreate = getIssue('project=' + pkToCreateIssue + ' AND issuetype=Task')
    }
            
    def issueEqualInProjectFound = false
    def issueEqualInProjectFoundHasIssueLink = false;
    def issueEqualInProjectFoundId = "blabla"
    
    if(issuesInProjectToCreate.size() > 0){
        for(i in issuesInProjectToCreate){
            def issueIdInIssueLinked = i.fields["issuelinks"]["outwardIssue"]["id"][0]
            if(issueIdInIssueLinked == issueId){
                issueEqualInProjectFound = true
                issueEqualInProjectFoundHasIssueLink = true
                issueEqualInProjectFoundId = i.id
                break
            }else{
                if(i.fields["summary"].toString().toLowerCase().replaceAll(" ", "") == issueSummary.toString().toLowerCase().replaceAll(" ", "") && issueIdInIssueLinked != issueId){
                    issueEqualInProjectFound = true
                    issueEqualInProjectFoundHasIssueLink = false
                    issueEqualInProjectFoundId = i.id
                    break
                }
            }
        }
    }
    if(issueEqualInProjectFound == false){
        /* Will create issue */
        logger.info("Issue not in project: '" + pkToCreateIssue + "'! Creating and linking")
        def issueTypeId = getIssueTypeId("Task", false)
        def fieldEpicLinkId = getCustomFieldId('Epic Link')
        def thisIssueEpicSummary = getIssueEpicSummaryByKey(issue.fields["project"]["key"], issue.fields[fieldEpicLinkId].toString())
        def issueEpicKeyToCreate = getIssueEpicKeyBySummary(pkToCreateIssue, thisIssueEpicSummary)
    	def issueCreated = createIssue(issueSummary, pkToCreateIssue, issueTypeId, issueEpicKeyToCreate)
        def issueCreatedId = issueCreated.id
        def issueLinkCreated = linkIssue(issueId, issueCreatedId)
        logger.info("Creating issue with id: " + issueCreatedId + " and linking to issue with id: " + issueId + " and name: " + issueSummary + " in project '" + pkToCreateIssue + "'")
    }else{
        /* Update issue */
        logger.info("Issue already in project: '" + pkToCreateIssue + "' with id:'" + issueEqualInProjectFoundId + "'! Just updating linking")
        if(issueEqualInProjectFoundHasIssueLink == false){
            logger.info("Issue only has the same name! Creating linking between issues ")
            logger.info(issueEqualInProjectFoundId)
            linkIssue(issueId, issueEqualInProjectFoundId)
        }else{}
    }
}

if(statusThatUpdatedTo != null){
    for(statusAndProjectKey in relationStatusProjectKey){
        if(statusAndProjectKey.status.toString().toLowerCase().replaceAll(" ", "") == statusThatUpdatedTo.toString().toLowerCase().replaceAll(" ", "")){
            statusTrigger = statusAndProjectKey.status
            projectKeyToCreateIssue = statusAndProjectKey.projectKey
            if(!(projectKeyToCreateIssue instanceof java.util.ArrayList)){
                updateKanban(issueId, issueSummary, issueStatus, statusTrigger, projectKeyToCreateIssue);
            }else{
                for(pkToCreateIssue in projectKeyToCreateIssue){
                    updateKanban(issueId, issueSummary, issueStatus, statusTrigger, pkToCreateIssue);
                }
            }
        }
    }
}

/* Gabriel Tessarini */
