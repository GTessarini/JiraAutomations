//Define the labels with name of each user and set it slack conversation links
def slacks = [
    ["name": "ELVIS", "link": "https://hooks.slack.com/services/XXXXXXX/YYYYYYY/ZZZZZZZZZZZZZZZZZZZ"],
    ["name": "PRISCILLA ", "link": "https://hooks.slack.com/services/XXXXXXX/YYYYYYY/ZZZZZZZZZZZZZZZZZZZ"]
]

def wasAttachmentAdded = false
def attachmentName = ""

/* Check if attachment has updated the issue */
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

def thisUpdate = getChangeLogIssue(issue.id, 1, 0)
for(itemUpdated in thisUpdate["items"]){
    if(wasAttachmentAdded == false){
        for(item in itemUpdated){
            if(
                itemUpdated.field.toString().toLowerCase().trim() == "attachment"
                && itemUpdated["toString"] != null
            ){
                wasAttachmentAdded = true
                attachmentName = itemUpdated["toString"].toString().trim()
                break
            }
        }
    }
}

if(wasAttachmentAdded == false){
    logger.info("No attachment was added")
    return
}else{
    logger.info("Attachment was added")
}
    
//Get all label values in this issue
def labels = issue.fields["labels"];
//Get issue summary
def summary = issue.fields["summary"];

// Check if is a worklog of a task or a subtask
if(issue != null && issue.fields["issuetype"]["subtask"] == true){
    summary = issue.fields["parent"].fields["summary"].toString() + " -> " + summary + "";
    //Get parent label if subtask's is not filled
    if(labels.size() == 0){
        labels =  get("/rest/api/2/issue/" + issue.fields["parent"]["id"] + "")
                    .asObject(Map)
                    .body
                    .fields["labels"];
    }
}
//Send a Slack for each user with label in this issue
for(label in labels){
    for(slack in slacks){
        if(slack.name.toLowerCase() == label.toString().toLowerCase()){
            post(slack.link)
                .header("Content-Type", "application/json")
                .body(
                    [
                        text: "JIRA -> *" + summary + "*:\n"+ "https://business.atlassian.net/browse/" + issue["key"] + "\n"+ "An attachment was added:" + "\n" + attachmentName
                    ]
                )
                .asString()
        }
    }
}

/* Gabriel Tessarini */
