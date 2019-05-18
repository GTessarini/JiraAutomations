/* Check if is subtask */
if(issue == null || issue.fields["issuetype"]["subtask"] == true){
    return
}

// Retrieve all issue types
def typeResp = get('/rest/api/2/issuetype').asObject(List)
assert typeResp.status == 200
def issueTypes = typeResp.body as List<Map>

// Set the basic subtask issue details (name [summary] and type)
def summary = ["Subtask 1", "Subtask 2", "Subtask 3"]
def issueType = "Sub-task"

def issueTypeId = issueTypes.find { it.subtask && it.name == issueType }?.id

if(issueTypeId == null){ 
    logger.info("No issueTypeId found for this issueType '" + issueType + "'")
}else{
     // Create each subtask defined in summary
    logger.info("Will create subtasks")
    def labels = issue.fields["labels"]
    if(labels.size() == 0){
        labels = [""]
    }
    def subTaskBody = [
        fields: [
            project: (issue.fields as Map).project,
            issuetype: [
                id: issueTypeId
            ],
            parent: [
                id: issue.id
            ],
            summary: "",
            labels: labels
        ]
     ]
     for(subtaskName in summary){          
        subTaskBody.fields["summary"] = subtaskName
         if(issue.fields["assignee"]){
            subTaskBody.fields["assignee"] = [
                id: issue.fields["assignee"]["accountId"]
            ]
        }
        // Post the subtask on Jira
        def resp = post("/rest/api/2/issue")
                .header("Content-Type", "application/json")
                .body(subTaskBody)
                .asObject(Map)
        def subtask = resp.body
        assert resp.status >= 200 && resp.status < 300 && subtask && subtask.key != null
    }
}

/* Gabriel Tessarini */
