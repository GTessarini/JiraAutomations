// Retrieve all issue types
def typeResp = get('/rest/api/2/issuetype').asObject(List)
assert typeResp.status == 200
def issueTypes = typeResp.body as List<Map>

// Set the basic subtask issue details (name [summary] and type)
def summary = ["Subtask 1", "Subtask 2", "Subtask 3"]
def issueType = "Sub-task"

def issueTypeId = issueTypes.find { it.subtask && it.name == issueType }?.id

if(issueTypeId != null){ 
    // Create each subtask defined in summary
    for(s in summary){
        def createDoc = [
            fields: [
                project: (issue.fields as Map).project,
                issuetype: [
                    id: issueTypeId
                ],
                parent: [
                    id: issue.id
                ],
                summary: s
            ]
        ]
        // Post the subtask on Jira
        def resp = post("/rest/api/2/issue")
                .header("Content-Type", "application/json")
                .body(createDoc)
                .asObject(Map)
        def subtask = resp.body
        assert resp.status >= 200 && resp.status < 300 && subtask && subtask.key != null
    }
}else{
    logger.info("No issueTypeId found for this issueType '" + issueType + "'")
}

/* Gabriel Tessarini */
