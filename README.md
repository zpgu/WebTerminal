# WebTerminal
#### Command line terminal via web browsers, for SSH/Telnet/TN3270 accesses (xterm.js with Java backend)

Why another tool for terminal access? Plenty already available (in nodejs/python/...):
* good to have choices, not many with Java backend anyway
* some unique features (session/screen sharing/suspend/resume)

This application provides **terminal** access to any device that it can reach,  presenting it in your browser (w/ xterm.js),
features include session sharing, suspend/resume sessions, multiple modes of access (ssh/telnet/tn3270) to various types of target
devices, maximum compatibility using pty access to some difficult/crusty old telnet devices, such as terminal/console servers that
convert serial ports for legacy servers/network elements. It even provides a way to access TN3270 mainframe hosts: you read it right,
mainframes are still around.

### Spring boot with xterm.js, websocket for bidirectional data flow, pty for compatibility/flexibility

Requirements:

  - Java 11 or above (not a hard requirement, only because of dependencies)
  - maven (for build, compatible with your Java version)
  - Linux/unix based server for deployment for full functionality (Windows doesn't really have terminal and/or pty anyway)
  
### How to build/run:

```
- git clone https://github.com/zpgu/WebTerminal
- cd WebTerminal
- mvn clean install
- java -jar target/WebTerminal-0.9-SNAPSHOT.jar
```

Now navigate your browser to:
   https://ip-of-server:8443/
   
default logins:
  - admin / adminpass
  - user / userpass
   
UI is plain/ugly/incomplete but functional, definitely could use polish/functionality. From there, you can start new terminal sessions,
or act upon (view/join/...) existing sessions, etc.

Browser support depends on xterm.js, so probably newer versions of major browsers should work.

WebTerminal-0.9-SNAPSHOT.jar (typical Spring Boot fat jar) also takes command line arguments (in addition to all other standard options)
to read a csv file for GUI access control, and it will reload the file if it ever gets updated, sort of simulating a poor 
man's CRUD operations for the lack of such an admin interface to manage UI user accounts:

   `java -jar target/WebTerminal-0.9-SNAPSHOT.jar --webterminal.userFile=YourOwnCSVfile.csv`

csv file is very simple, with content like this (first line is header, these match the default accounts if not customized):
```
role,login,password
ADMIN,admin,adminpass
USER,user,userpass
```

When starting new sessions, you have the option to make them visible to everyone or not, while admin role accounts have visibility to all sessions.

While viewing the list of sessions, anyone can get into a visible session, either watch (read only), or join (read/write). Also
one can take over a visible session if so desired, and become the new owner. Owner can kill a session. Obviously anyone doing
an exit/logout on a session will terminate the session, as well as if the destination side disconnects for whatever reason.

Session owner can suspend the session (temporarily detach xterm.js front end, and resume later at different time/location) by click on
'Suspend' button, or navigating away via broswer "beforeunload" hook if supported.

Everyone on the same session will be updated with current list of participants at all time.

Terminal size propagation from session owner to all joined sessions whenever terminal is resized by owner.

Session interaction can be logged to a file on the server (default in /tmp directory on server, for review/audit later for example).

Design goals include having session sharing among interested parties to troubleshoot together, or as a group teaching/learning tool
when interacting on the same terminal, etc.

The pty mode offers great flexibility, demonstrated by the TN3270 access (forced to pty mode). It invokes the c3270 (part of x3270 suite
of programs) on the server for mainframe 3270 access. Likewise you can present any terminal based app (legacy or not) onto the browser
world in a similar way, with minimal effort.

For pty mode to work as configured, this app expects these command line programs available on the server: ssh/telnet/c3270

I named it webterminal and used org.webterminal namespace for lack of better choices (or imagination).

Project should load easily into your Java IDE of choice.

I hope you find this program useful. Your questions/suggestions/ideas/bug reports/contributions/offers are welcome.

### Security consideration:
* SSL certs included is self signed for obvious reason ($$$)
* device passwords are cleared immediately once used
* this app leads you to your device, but your device is the final guard againt unauthorized access.

### Areas for improvement:
* Javadoc (mostly auto-generated dummies), add tests
* UI (need better UI for sure)
* Better front end auth source/user management, like two factor auth, etc (csv as a proof of concept)
* Session logging to remote sink (right now local file only), session playback?
* Add more groups to control session visibility among users of the same group (currently only 2 groups: ADMIN and USER group)?
* Fixed list of destination based on user id (or group membership)
* ...
