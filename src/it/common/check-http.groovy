/*
    Copyright (c) 2026 Contributors to Eclipse Foundation. All rights reserved.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License v. 2.0, which is available at
    http://www.eclipse.org/legal/epl-2.0.

    This Source Code may also be made available under the following Secondary
    Licenses when the conditions for such availability set forth in the
    Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
    version 2 with the GNU Classpath Exception, which is available at
    https://www.gnu.org/software/classpath/license.html.

    SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
*/

def pause() {
    def trigger = new File(basedir, 'continue.txt')
    trigger.delete()
    println "Build paused. Run: touch ${trigger} to continue..."
    while (!trigger.exists()) { Thread.sleep(1000) }
}

if (project.properties['glassfish.check.pause']) {
    pause()
}

def port = project.properties['glassfish.http.port']
def reportFile = new File(project.build.directory, 'http-check-result.txt')
reportFile.parentFile.mkdirs()

def lastError = null
def waitMs = 1000
3.times {
    try {
        Thread.sleep(waitMs)
        def conn = new URI("http://localhost:${port}").toURL().openConnection() as HttpURLConnection
        conn.connect()
        def stream = conn.responseCode >= 400 ? conn.errorStream : conn.inputStream
        def response = stream.text
        if (response.contains("GlassFish")) {
            reportFile.text = "OK"
            lastError = null
            return
        } else {
            lastError = "ERROR: Response from http://localhost:${port} does not contain 'GlassFish'"
        }
    } catch (Exception e) {
        lastError = "ERROR: Could not connect to http://localhost:${port}: " + e.message
    }
    waitMs *= 2
}

if (lastError) {
    reportFile.text = lastError
}
