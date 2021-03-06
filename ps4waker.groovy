/**
*   Copyright 2015 Stuart Buchanan
* 
*   Licensed under the Apache License, Version 2.0 (the License); you may not use this file except
*   in compliance with the License. You may obtain a copy of the License at
* 
*       httpwww.apache.orglicensesLICENSE-2.0
* 
*   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*   on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*   for the specific language governing permissions and limitations under the License.
* 
*   PS4-Waker
* 
*   Co-Author Jeffrey Schiller
*   Date 2018-08-13 v1.0 Initial Release... Edited for PS4-Waker 
*   Author Stuart Buchanan
*   Date 2016-04-06 v1.1 Added User Authentication, added logging of response parsed http headers.
*   Date 2016-04-05 v1.0 Initial Release
**/ 
preferences {
    input("ServerIP", "text", title: "ServerIP", description: "Enter the IP Address of the PS4-Waker-REST Server")
    input("Port", "text", title: "Port", description: "Enter the TCP Port of the PS4-Waker-REST Server")
    input("PS4IP", "text", title: "Port", description: "Enter the IP Address of the Playstation 4")

}  
 
metadata {
    definition (name: "PS4-Waker", namespace: "ps4waker", author: "Jeffrey Schiller") {
        capability "Switch"
        capability "Refresh"
        capability "Polling"

        command "on"
        command "off" 
        command "info" 
}


    // simulator metadata
simulator {
        // status messages

        // reply messages
}

tiles(scale: 2) {
    multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4){
        tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
            attributeState "on", label:'On', action:"switch.off", icon:"https://raw.githubusercontent.com/ChronoStriker1/PS4-Waker-Smartthings-Handler/master/ps4on.png"
            attributeState "off", label:'Off', action:"switch.on", icon:"https://raw.githubusercontent.com/ChronoStriker1/PS4-Waker-Smartthings-Handler/master/ps4off.png"
        }
    }
    standardTile("on", "device.switch", width: 2, height: 2, decoration: "flat") {
            state "default", label:"On", action:"switch.on", icon:"https://raw.githubusercontent.com/ChronoStriker1/PS4-Waker-Smartthings-Handler/master/ps4on.png"
        }   
    standardTile("off", "device.switch", width: 2, height: 2, decoration: "flat") {
            state "default", label:"Off", action:"switch.off", icon:"https://raw.githubusercontent.com/ChronoStriker1/PS4-Waker-Smartthings-Handler/master/ps4off.png"
        }
    standardTile("refresh", "device.switch", width: 2, height: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }           
        main "switch"
        details(["switch","on","off","refresh"])
    }
}

def updated() {
    log.info "PS4-Waker Updated"
    state.dni = createDNI(settings.ServerIP, settings.Port)
    state.hostAddress = "${settings.ServerIP}, ${settings.Port}"
    refresh()
}

def installed() {
    log.info "PS4-Waker Updated"
    state.dni = createDNI(settings.ServerIP, settings.Port)
    state.hostAddress = "${settings.ServerIP}, ${settings.Port}"
    refresh()
}

def parse(String message) {
    def msg = stringToMap(message)

    if (!msg.containsKey("headers")) {
        log.error "No HTTP headers found in '${message}'"
        return null
    }

    // parse HTTP response headers
    def headers = new String(msg.headers.decodeBase64())
    def parsedHeaders = parseHttpHeaders(headers)
    log.debug "parsedHeaders: ${parsedHeaders}"
    if (parsedHeaders.status != 200) {
        log.error "Return Code: ${parsedHeaders.status} Server error: ${parsedHeaders.reason}"
        return null
    }

    // parse HTTP response body
    if (!msg.body) {
        log.error "No HTTP body found in '${message}'"
        return null
    } else {
    def body = new String(msg.body.decodeBase64())
    parseHttpResponse(body)
    log.debug "body: ${body}"
    }
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]
    return result
}

private def parseHttpResponse(String data) {
    log.debug("parseHttpResponse(${data})")
    def splitresponse = data.split(",")
    def status
    if (splitresponse[0] != '{"status":"success"}') {
        def port1 = splitresponse[7].split(":")
        def port = port1[1]
        def status1 = splitresponse[3].split(":")
        status = status1[1]
        if (status == '"OK"'){
            createEvent(name: "switch", value: "open", descriptionText: "$device.displayName is on", isStateChange: "true")
        } else if (status == '"Standby"'){
            createEvent(name: "switch", value: "closed", descriptionText: "$device.displayName is off", isStateChange: "true")
        }
    } else {
        status = "Redo"
    }
    log.debug ("Status: ${status}")
    return status
}

def poll() {
    log.debug "Executing poll Command"
    refresh()
}

def refresh() {
    log.debug "Executing Refresh Command"
    getstatus()
}

def on() {
    log.debug "Executing On Command"
    open()
}

def off() {
    log.debug "Executing Off Command"
    close()
}

def open(){
try {
    log.debug "Executing On"
    def openResult = getopen()
    log.debug "${openResult}"
    sendHubCommand(openResult)
    }
    catch (Exception e)
    {
        log.debug "Hit Exception $e"
    }
createEvent(name: "switch", value: "open", descriptionText: "$device.displayName is on", isStateChange: "true")
}

def close(){
try {
    log.debug "Executing Off"
    def closeResult = getclose()
    log.debug "${closeResult}"
    sendHubCommand(closeResult)
    }
    catch (Exception e)
    {
        log.debug "Hit Exception $e"
    }
createEvent(name: "switch", value: "closed", descriptionText: "$device.displayName is off", isStateChange: "true")
}

def getopen(){
    def headers = [:]
    headers.put("HOST","${settings.ServerIP}:${settings.Port}")
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/ps4/${settings.PS4IP}/on",
        headers: headers
    )
    return result
}

def getclose(){
    def headers = [:]
    headers.put("HOST","${settings.ServerIP}:${settings.Port}") 
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/ps4/${settings.PS4IP}/off",
        headers: headers
    )
    return result
}

def getstatus(){
    def headers = [:]
    headers.put("HOST","${settings.ServerIP}:${settings.Port}") 
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/ps4/${settings.PS4IP}/info",
        headers: headers
    )
    return result
}

private def delayHubAction(ms) {
    log.debug("delayHubAction(${ms})")
    return new physicalgraph.device.HubAction("delay ${ms}")
}
