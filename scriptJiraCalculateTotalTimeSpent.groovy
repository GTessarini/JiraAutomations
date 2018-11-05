// Get custom fields
def customFields = get("/rest/api/2/field")
        .asObject(List)
        .body
        .findAll { 1 == 1 } as List<Map>
        
// Get required fields for calculate time
def input1CfId = customFields.find { it.name == 'Time Activity 1' }?.id
def input2CfId = customFields.find { it.name == 'Time Activity 2' }?.id
def input3CfId = customFields.find { it.name == 'Time Activity 3' }?.id

// Get field to set calculated time
def outputCfId = customFields.find { it.name == 'Time Spent' }?.id 

def projectKey = "YOUR-PROJECT-KEY"

if (issue == null || ((Map)issue.fields.project).key != projectKey) {
    logger.info("Wrong Project \${issue.fields.project.key}")
    return
}

// Set text inputted in each field used
def inputs = [
 issue.fields[input1CfId] as String,
 issue.fields[input2CfId] as String,
 issue.fields[input3CfId] as String
}

def outputHour = 0;
def outputMinute = 0;

for (input in inputs) {
    if(input == null){
        logger.info("Calculation using \${input} was not possible")
        return
    }else{
        def hours = ""
        if(input.indexOf("h") > -1 && input.split("h").length > 0){
            hours = input.split("h")[0];
            outputHour += hours as Integer;
        }
        def minutes = ""
        if(input.split("h ").length > 1 && input.split("h ")[1] != input){
            minutes = input.split("h ")[1].split("m")[0]
        }else if(input.split("m").length > 0){
            minutes = input.split("m")[0]
        }
        outputMinute += (minutes != input ? minutes as Integer : 0)
    }
}

while(outputMinute >= 60){
    outputHour = outputHour + 1
    outputMinute -= 60
}

logger.info(outputHour + "h " + outputMinute + "m")

def output = (outputHour == 0 ? "" : (outputHour + "h ")) + (outputMinute == 0 ? "00" : outputMinute)  + "m"

put("/rest/api/2/issue/"+ issue.key + "")
    .header("Content-Type", "application/json")
    .body([
    fields:[
        (outputCfId): output.toString()
    ]
]) .asString()

/* Gabriel Tessarini */
