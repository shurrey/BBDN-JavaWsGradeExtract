# BBDN-JavaWsGradeExtract

Sample Web Service Client and Grade Extract

Follow the steps below to enable the Blackboard web services and launch the sample grade extract application.  The samples in this project rely entirely on the Blackboard Web Services to extract data.  The only requirements are a Blackboard Learn environment and a locally installed Java SDK version 1.6 or later.


This readme has been written primarily for a Windows client deployment (server version of Blackboard does not matter), but could just as easily be deployed in a UNIX environment.  Just use UNIX commands where appropriate.

### Step 0: Download and Install the Java SDK

If you don't currently have the Java SDK on your environment, download it from:
  http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
and install it.  Be sure to install to a location that does not contain spaces
in the path (i.e. C:\java\jdk1_7\ or /usr/local/java/jdk1_7/).

After installing, make sure your JAVA_HOME environment variable is set (on
Windows you can check this by running the command "echo %JAVA_HOME% which
should display the home directory).  If JAVA_HOME is NOT set, you can either
set is as a global environment variable, or just set it for your session before
running the extract commands, i.e:

  set JAVA_HOME=C:\java\jdk1_7


### Step 1: Download the Required 3rd-party Libraries and Tools

This sample web services application requires some 3rd-party libraries in order to run.  If you use different versions than those referenced below, be sure to update the configuration (application.properties) accordingly.

1) DOWNLOAD axis2-1.6.2-bin.zip from:<br />
     http://axis.apache.org/axis2/java/core/download.cgi<br />
   and unzip it to the ./lib/ directory of this project.  If a ./lib/ directory
   does not yet exist, create it.  When you are done, you should have the
   following folder:<br />
     ./lib/axis2-1.6.2/<br />
   which contains sub-folders folders such as /lib/, /conf/, /samples/

2) COPY ./lib/axis2-1.6.2/repository/modules/addressing-1.6.2.mar to the
   ./lib/axis2-1.6.2/lib/ directory.  When you are done, you should have the
   following file in your project:<br />
     ./lib/axis2-1.6.2/lib/addressing-1.6.2.mar

3) DOWNLOAD rampart-dist-1.6.2-bin.zip from:<br />
     http://axis.apache.org/axis2/java/rampart/download/1.6.2/download.cgi
   and unzip it to the ./lib/ directory of this project.  When you are done,
   you should have the following folder:<br />
     ./lib/rampart-1.6.2/<br />
   which contains sub-folders folders such as /lib/, /modules/, /samples/

4) COPY ./lib/rampart-1.6.2/modules/rampart-1.6.2.mar to the
   ./lib/axis2-1.6.2/lib/ directory.  When you are done, you should have the
   following file in your project:<br />
     ./lib/axis2-1.6.2/lib/rampart-1.6.2.mar

5) EDIT ./lib/axis2-1.6.2/conf/axis2.xml and find the following line in the
   file (somewhere around line 256):<br />
     &lt;module ref="addressing"/&gt;<br />
   below that line, add the following line to the file:<br />
     &lt;module ref="rampart"/&gt;<br />
   and save the file and close it.

6) DOWNLOAD apache-ant-1.9.0-bin.zip from:<br />
     http://ant.apache.org/bindownload.cgi<br />
   and unzip it to the ./tools/ directory of this project.  If a ./tools/
   directory does not yet exist, create it.  When you are done, you should have
   the following folder:<br />
     ./tools/apache-ant-1.9.0/<br />
   which contains sub-folders folders such as /bin/, /lib/, /etc/


### Step 2: Enabled Blackboard Web Services

1) As a System Administrator in Blackboard, navigate to:
     System Admin -> Web Services (in the Building Blocks module)

2) Minimally activate the following web services by checking the box next to
   each and selecting Availability -> Make Available.  You could also just make
   all the web services available.
    - Context.WS
    - Course.WS
    - CourseMembership.WS
    - Gradebook.WS
    - User.WS
    - Util.WS

3) Repeat (2) above for Discoverability -> Make Discoverable


### Step 3: Get the Tool Registration Password for Proxy Tools

1) As a System Administrator in Blackboard, navigate to:
     System Admin -> Building Blocks -> Proxy Tools

2) Click "Manage Global Properties"

3) Set the "Proxy Tool Registration Password".  Or, if it is already set, note
   the password as it will be used later in the configuration.


### Step 4: Initial Application Configuration

1) Copy the application.properties.template file to a file named
   application.properties in the same directory.

2) Edit application.properties and delete the notice at the top of the
   properties file.

3) Go through the properties file and enter the Web Service Configuration


### Step 5: Building the Web Services Client

Before you can run the web services client, you must build the client from the web service WSDLs.  The commands below will build and compile the client classes needed to run the sample and extract provided with this project.

1) On the command line, navigate to %WS_SAMPLE_HOME%

2) Run the following command:
     tools\apache-ant-1.9.0\bin\ant.bat wsdl.compile

   When it is complete, you should have the following new directories:
     /_classes_ws_/
     /_src_ws_/

   NOTE: If you get the following error, you may need to set your JAVA_HOME
   variable either in the environment or as a session variable as noted at
   the beginning of this readme file.
     BUILD FAILED
       build.xml:49: Unable to find a javac compiler;

3) Run the following command:
     tools\apache-ant-1.9.0\bin\ant.bat client.compile

   When it is complete, you should have the following new directory:
     /_classes_/


### Step 6: Run the Sample Extract to Validate the Configuration

In this step, you will launch the sample application from the command line in
order to validate the web service configuration.  For this step, you need at
least one course in the system that has at least one student user with grades.
These steps assume a deployment directory of %WS_SAMPLE_HOME% (i.e. something
like c:\bbsamplews\) which could be any directory on your file system.

1) Identify one sample course that contains some student enrollments and grades
   and provide that courseId (sample.courseId) for the Sample Extract
   Configuration in application.properties

2) On the command line, navigate to %WS_SAMPLE_HOME%

3) Run the following command:
     tools\apache-ant-1.9.0\bin\ant.bat launchSample

   and observe the output.  It should look something like to:
     [java] 00:03:28 | INFO  |  43:SampleExtractApp | Creating Web Service Client
     [java] 00:03:28 | INFO  | 155:WebServiceClient | Initializing...
     [java] 00:03:29 | INFO  | 199:WebServiceClient | Checking versions...
     ... and so on ...
     [java] 00:03:31 | INFO  | 258:WebServiceClient | Registering Tool...
     [java] Exception in thread "main" org.apache.axis2.AxisFault: [Context.WS006]Proxy Tool is not currently available
     [java] 	at org.apache.axis2.util.Utils.getInboundFaultFromMessageContext(Utils.java:531)

   which is expected, as the newly created tool must now be activated.

4) As a System Administrator in Blackboard, navigate to:
     System Admin -> Building Blocks -> Proxy Tools
   
   And "Edit" the Proxy Tool created by the configuration in
   application.properties.
   
   Set the "Availability" to "yes" and Submit the change.

5) Re-run the command in (3) above and observe the output, it should look
   something like the following:
     [java] 00:10:22 | INFO  |  43:SampleExtractApp | Creating Web Service Client
     [java] 00:10:22 | INFO  | 155:WebServiceClient | Initializing...
     [java] 00:10:22 | INFO  | 199:WebServiceClient | Checking versions...
     ... and so on ...
     [java] 00:10:25 | INFO  | 258:WebServiceClient | Registering Tool...
     [java] 00:10:25 | WARN  | 271:WebServiceClient | Errors/warnings registering tool: 
     [java] 00:10:25 | WARN  | 274:WebServiceClient |  - Cannot reregister an activated client
     [java] 00:10:25 | INFO  | 279:WebServiceClient | Initialization complete.
     [java] 00:10:25 | INFO  |  70:SampleExtractApp | Loading course: [your course id]
     [java] 00:10:25 | INFO  |  78:SampleExtractApp |  Loading users for course: [your course id]
     ... user details ...
     [java] 00:10:26 | INFO  |  86:SampleExtractApp |  Loading student memberships for course: [your course id]
     ... student membership details ...
     [java] 00:10:27 | INFO  |  95:SampleExtractApp |  Loading columns for course: [your course id]
     ... column details ...
     [java] 00:10:28 | INFO  | 103:SampleExtractApp |  Loading grades for course: [your course id]
     ... grade details details ...
     [java] 00:10:34 | INFO  | 112:SampleExtractApp | EXTRACT COMPLETE

  which indicates that the client has connected and downloaded data from the
  course.  Class blackboard.ws.client.SampleExtract is the Java source file
  responsible for this extract.

6) Move one level down from the current directory into the /tools/ directory
  (i.e. "cd tools") and run the following command:

     apache-ant-1.9.0\bin\ant.bat -f ..\build.xml launchSample

  and observe the output which should match that of (5) above.  This validates
  that the command can be run from any directory on the system as long as the
  location of the build.xml file is provided.

### Step 7: Run the Full Grade Extract Application

The last set of properties in application.properties apply to the full grade
extract application.  When initially testing the full extract, it is best to
make use of the "app.maxCourses" and "app.courseIdContains" properties to keep
the initial load to a minimum.  Based on testing/timing results, this result
set could be expanded.  It is not recommended to run this extract for more than
a few thousand courses at a time.

After configuring application.properties, run the following command to launch
the full extract:
  tools\apache-ant-1.9.0\bin\ant.bat launchExtract

Application logs and reports can be found in the /_logs_/ and /_reports_/
directories respectively.


### Additional Command Line Options

providing courseId on the command line for the sample extract
tools\apache-ant-1.9.0\bin\ant.bat launchSample -DcourseId=[TargetCourseId]

providing properties file on the command line for the full extract
tools\apache-ant-1.9.0\bin\ant.bat launchSample -DappConfig=/path/to/yourconfig.properties

running the commands from a location other than the project home directory
path\to\ant_home\bin\ant.bat launchExtract -f path\to\build.xml

setting JAVA_HOME with the command
Instead of setting JAVA_HOME as an environment variable, you can add it to your
command-line launch.  Windows example:

  set JAVA_HOME=C:\java\jdk1_7 & tools\apache-ant-1.9.0\bin\ant.bat launchExtract

Unix example:

  JAVA_HOME=/usr/local/java/jdk1_7; tools/apache-ant-1.9.0/bin/ant launchExtract


### Logging

All command line and logging output is defined in /src/log4j.properties


### Modifying the Source Code

You should not directly modify any of the generated sources or classes found
in the /_X_/ directories.  The web service client and either of the two extract
class can be modified in the /src/ folder:
  blackboard.ws.client.WebServiceClient.java
  blackboard.ws.client.SampleExtractApp.java
  blackboard.ws.client.GradeExtractApp.java

If you make any changes to the above sources, you must re-compile the client
classes to pick up the changes:

  tools\apache-ant-1.9.0\bin\ant.bat client.compile

before running either of the sample extracts.
