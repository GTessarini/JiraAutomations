def issueId = worklog.issueId
def projectKey = "YOUR-PROJECT-KEY"

if(issueId == null){
    return
}
// Get the issue of this worklog
def issue = get("/rest/api/2/issue/" + issueId + "" )
    .asObject(Map)
    .body
    
// Check if is a worklog of a task or a subtask
if(issue == null || issue.fields["issuetype"]["subtask"] == false){
    return
}else{
    // Retrieve all the subtasks of this issue's subtask parent
    def parentKey = issue.fields["parent"]["id"]
    def taskIssue = get("/rest/api/2/issue/" + parentKey + "" )
            .asObject(Map)
            .body
            
    def allSubtasks = taskIssue.fields["subtasks"]
    def subTasksTimeSpent = 0
    def timeSpentInSeconds = 0
    
    for(subtask in allSubtasks){
        def subTaskIssue = get("/rest/api/2/issue/" + subtask["id"] + "")
            .asObject(Map)
            .body
            
        // Get timeSpent in seconds
        timeSpentInSeconds = subTaskIssue.fields["timetracking"]["timeSpentSeconds"]
        timeSpentInSeconds = (timeSpentInSeconds == null ? 0 : (timeSpentInSeconds as Integer))
        subTasksTimeSpent += timeSpentInSeconds 
    }
    if(subTasksTimeSpent != 0){
        def timeKind = "s" /* Seconds */
        // Convert subtasks time spent from seconds to minutes
        if(subTasksTimeSpent >= 60){
            subTasksTimeSpent = subTasksTimeSpent / 60
            timeKind = "m" /* Minutes */
            // Convert time spent from minutes to hours
            def subTasksTimeSpentHours = 0
            while(subTasksTimeSpent >= 60){
                subTasksTimeSpentHours = subTasksTimeSpentHours + 1
                subTasksTimeSpent -= 60
            }
            subTasksTimeSpent = (subTasksTimeSpentHours == 0 ? "" : (subTasksTimeSpentHours + "h ")) + (subTasksTimeSpent == 0 ? "0" : subTasksTimeSpent) + timeKind
        }
        logger.info("subTasksTimeSpent: " + subTasksTimeSpent);
        
        // Get custom fields
        def customFields = get("/rest/api/2/field")
                .asObject(List)
                .body
                .findAll { 1 == 1 } as List<Map>
                
        if(customFields == null){
            return
        }
        def outputCfId = customFields.find { it.name == 'Time Spent' }?.id 
        if (((Map)issue.fields.project).key != projectKey) {
            logger.info("Wrong Project \${issue.fields.project.key}")
            return
        }
        // Now update the parent issue
        put("/rest/api/2/issue/" + parentKey + "")
            .header("Content-Type", "application/json")
            .body([
            fields:[
                (outputCfId): subTasksTimeSpent
            ]
        ]) .asString()
    }
}
/* Gabriel Tessarini */
