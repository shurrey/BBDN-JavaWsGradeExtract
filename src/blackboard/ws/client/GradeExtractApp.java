
package blackboard.ws.client;

import java.io.*;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

import blackboard.ws.course.CourseWSStub.CourseVO;
import blackboard.ws.coursemembership.CourseMembershipWSStub.CourseMembershipVO;
import blackboard.ws.gradebook.GradebookWSStub.ColumnVO;
import blackboard.ws.gradebook.GradebookWSStub.ScoreVO;
import blackboard.ws.user.UserWSStub.UserVO;

public class GradeExtractApp
{
  private static final org.apache.commons.logging.Log _LOG = //
    org.apache.commons.logging.LogFactory.getLog( GradeExtractApp.class );

  private final Properties _appConfig;
  
  private final String _delimiter;
  private final String _courseIdContains;
  private final int _maxCourses;
  private final int _wsClientBatchSize;
  private final int _wsClientBatchDelay;
  private final int _batchWaitSize;
  private final int _batchWaitDelay;
  private final boolean _externalGradeOnly;

  private List<String> _errors = new ArrayList<String>();

  private GradeExtractApp( Properties appConfig )
  {
    // Hold the properties for future use.
    _appConfig = appConfig;

    // Load the application-specific properties
    _delimiter = appConfig.getProperty( "app.dataDelimiter", "|" ).trim();
    _courseIdContains = appConfig.getProperty( "app.courseIdContains", "" ).trim();
    _maxCourses = Integer.parseInt( appConfig.getProperty( "app.maxCourses", "-1" ).trim() );
    _wsClientBatchSize = Integer.parseInt( appConfig.getProperty( "app.wsClientBatchSize", String.valueOf( Integer.MAX_VALUE ) ).trim() );
    _wsClientBatchDelay = Integer.parseInt( appConfig.getProperty( "app.wsClientBatchDelay", String.valueOf( Integer.MAX_VALUE ) ).trim() );
    _batchWaitSize = Integer.parseInt( appConfig.getProperty( "app.batchWaitSize", String.valueOf( Integer.MAX_VALUE ) ).trim() );
    _batchWaitDelay = Integer.parseInt( appConfig.getProperty( "app.batchWaitDelay", String.valueOf( Integer.MAX_VALUE ) ).trim() );
    _externalGradeOnly = Boolean.valueOf( appConfig.getProperty( "app.filterOnExternalGrade", "false" ) );
  }

  private String getOutputLocation() {
    return _appConfig.getProperty( "app.outputFile", "STDOUT" );
  }

  private boolean isStdOut() {
    return "stdout".equalsIgnoreCase( getOutputLocation() );
  }

  /**
   * Sample gradebook extract that uses only the Blackboard Learn web services
   * to extract data.  All of the invocations are done via the sample client
   * in this package (WebServiceClient).
   * 
   * @param args
   *   arg[0]=/path/to/application.properties
   *   arg[1]=/path/to/outputfile.csv (optional)
   */
  public static void main( String[] args ) throws IOException
  {
    // validate the arguments
    if( null == args || args.length < 1 || args.length > 2 ) {
      throw new IllegalArgumentException("Usage: arg[0]=/path/to/application.properties, arg[1]=/path/to/outputfile (optional)");
    }

    // Configuration file is argument (args[0])
    File appConfigFile = new File(args[0]);
    if( !appConfigFile.exists() ) {
      throw new IllegalArgumentException("Application properties file [" + appConfigFile + "] does not exist.");
    }

    // Output location is argument two (args[1])
    String appOutputLocation = null;
    if( args.length > 1 ) {
      appOutputLocation = args[1].trim();
    }

    // Load the properties file for the web service client and application
    final Properties appConfig = new Properties();
    InputStream is = null;
    try {
      is = new FileInputStream(appConfigFile);
      appConfig.load( is );
    }
    finally {
      if( null != is ) { is.close(); }
    }

    // If an output location was provided, override the configuration
    if( null != appOutputLocation ) {
      appConfig.put( "app.outputFile", appOutputLocation );
    }

    Date start = Calendar.getInstance().getTime();
    _LOG.info( "EXTRACT STARTED : " + start );

    GradeExtractApp app = new GradeExtractApp( appConfig );
    try {
      app.doMain();
    }
    catch( Exception e ) {
      app._errors.add( e.getMessage() );
      _LOG.error( e.getMessage(), e );
    }

    _LOG.info( "EXTRACT START TIME: " + start );
    _LOG.info( "EXTRACT COMPLETED : " + Calendar.getInstance().getTime() );
    if ( !app._errors.isEmpty() ) {
      _LOG.error( "ERRORS(" + app._errors.size() + "):" );
      for ( String message : app._errors ) {
        _LOG.error( " - " + message );
      }
    }
  }

  private void doMain() throws IOException, RemoteException
  {
    // Get a new instance of a web service client and initialize it.
    WebServiceClient wsClient = newWebServiceClient( null );

    // Load the courses
    final CourseVO[] courses;
    _LOG.info( "Loading courses..." );
    if( null == _courseIdContains || _courseIdContains.trim().length() == 0 ) {
      // Load all the available courses in the system.
      courses = wsClient.getAllCourses();
    }
    else {
      // load by criteria.
      courses = wsClient.getCoursesBySearch( "CourseId", "Contains", _courseIdContains );
    }
    _LOG.info( "Total number of courses: " + courses.length );

    // Sort by courseId (ascending) for report comparison
    Arrays.sort( courses, new Comparator<CourseVO>() {
      public int compare( CourseVO o1, CourseVO o2 ) {
        return o1.getCourseId().compareTo( o2.getCourseId() );
      }
    } );

    PrintStream dataOut = null;
    File tmpFile = null;
    File outFile = null;
    try {
      // Set the output, either a file or System.out.
      if ( isStdOut() ) {
        dataOut = System.out;
      }
      else {
        // Create a temporary file based on the target file.  We will write to
        // temporary file during execution then rename to the target file when
        // the process is complete.
        outFile = new File( getOutputLocation() );
        outFile.delete(); // DELETE EXISTING (OLD) REPORT
        File outLocFileParent = outFile.getParentFile();
        outLocFileParent.mkdirs();
        tmpFile = File.createTempFile( outFile.getName(), ".tmp", outLocFileParent );
        dataOut = new PrintStream( new BufferedOutputStream( new FileOutputStream(tmpFile)) );
      }

      printHeader( dataOut );

      // Iterate through all of the courses.
      for ( int ii = 0; ii < courses.length; ii++ )
      {
        if( ii == _maxCourses ) {
          _LOG.info("Maximum course limit reached, ending.");
          break;
        }

        // Web service batch check.  If the batch size has been reached, then
        // we logout of our current connection and open a new web service
        // client.  This prevents timeout errors.
        if ( (0 != ii) && (_wsClientBatchSize > 0) && (0 == (ii % _wsClientBatchSize)) ) {
          _LOG.info( "Web service client batch size [" + _wsClientBatchSize + "] reached, getting new client after ["+_wsClientBatchDelay+"] millisecond sleep." );
          quietSleep( _wsClientBatchDelay );
          wsClient = newWebServiceClient( wsClient );
        }

        // Batch wait delay check.  If the batch wait size has been reached,
        // then we pause for a specified amount of time to reduce impact on
        // the server as web service calls (especially for grades) can be
        // costly.
        if ( (0 != ii) && (_batchWaitSize > 0) && (0 == (ii % _batchWaitSize )) ) {
          _LOG.info( "Batch wait size [" + _batchWaitSize + "] reached, sleeping for [" + _batchWaitDelay + "] milliseconds before next batch." );
          quietSleep( _batchWaitDelay );
        }

        final CourseVO course = courses[ii];
        if( null == course ) {
          String message = "Found null course in results[" + ii + "], this should not happen, skipping.";
          _errors.add( message );
          _LOG.warn(message);
          continue; // Can't do anything with this course.
        }

        _LOG.info( "Course " + (ii+1) + ": " + course.getCourseId() );
        try {
          doCourse( course, wsClient, dataOut );
        }
        catch (Exception e ) {
          String message = "Error processing course [" + course.getCourseId() + "]: " + e.getMessage();
          _errors.add( message );
          _LOG.error( message, e );
        }
      } // end for courses
    }
    finally {
      if( (null != dataOut) && (dataOut != System.out) ) {
        _LOG.info("Closing report file stream.");
        dataOut.close();
        tmpFile.renameTo( outFile );
      }
      if( null != wsClient ) {
        wsClient.logout();
      }
    }
  } // end doMain()

  private void doCourse( CourseVO course, WebServiceClient wsClient, PrintStream dataOut ) throws RemoteException
  {
    // Get the course memberships and map them to their userPkId for quick lookup
    Map<String, CourseMembershipVO> membersByUserIdMap = new HashMap<String, CourseMembershipVO>();
    try {
      // Load the course memberships by Student role. This should probably be
      // made an externally configured property.
      CourseMembershipVO[] members = wsClient.getMembershipsByCoursePkIdAndRoleIds( course.getId(), new String[] {"S"} );
      _LOG.info( " Total student enrollments for course: " + members.length );
      if( 0 == members.length ) {
        _LOG.info( " No students = no grades, skipping course." );
        return; // There is no need to do anything else in this loop
      }
      // Do the mapping
      for ( CourseMembershipVO member : members ) {
        membersByUserIdMap.put( member.getUserId(), member );
      }
    }
    catch( Exception e ) {
      String message = "Failed to load memberships for course [" + course.getCourseId() + "].";
      _errors.add( message );
      _LOG.error( message, e );
      return;
    }

    // Load the columns of the gradebook for this course.
    ColumnVO[] columns;
    try {
      if( _externalGradeOnly ) {
        columns = new ColumnVO[] { wsClient.getExternalGradeColumnByCoursePkId( course.getId() ) };
      }
      else {
        columns = wsClient.getColumnsByCoursePkId( course.getId() );
      }
    }
    catch( Exception e ) {
      String message = "Failed to load columns for course [" + course.getCourseId() + "].";
      _errors.add( message );
      _LOG.error( message, e );
      return;
    }

    _LOG.info( " Total columns to extract for course: " + columns.length );

    // Sort the columns by position for report comparison
    Arrays.sort( columns, new Comparator<ColumnVO>() {
      public int compare( ColumnVO o1, ColumnVO o2 ) {
        return Integer.valueOf(o1.getPosition()).compareTo( o2.getPosition() );
//        return o1.getColumnDisplayName().compareTo( o2.getColumnDisplayName() );
      }
    } );

    // Load the users for the course.  Note that this number could differ
    // from the number of course memberships as we are NOT filtering by
    // course role here (i.e. instructors are included in this load).
    UserVO[] users = wsClient.getUsersByCoursePkId( course.getId() );
    _LOG.info( " Total users for course: " + users.length );

    // Sort the users by username for report comparison
    Arrays.sort( users, new Comparator<UserVO>() {
      public int compare( UserVO o1, UserVO o2 ) {
        return o1.getName().compareTo( o2.getName() );
      }
    } );

    // Load all the scores for the course
    _LOG.info( " Loading grades..." );
    ScoreVO[] scores = wsClient.getScoresByCoursePkId( course.getId() );
    _LOG.info( " Total grades for course: " + scores.length );

    for ( ColumnVO column: columns )
    {
      for ( UserVO user : users )
      {
        // Look up the membership.
        CourseMembershipVO member = membersByUserIdMap.get( user.getId() );
        if( null == member ) {
          // This can happen if the user is a non-student in the course or
          // if the user's enrollment is disabled...
          continue;
        }

        // Lookup the score for this membership and column.
        ScoreVO score = lookupScore( column, member, scores );

        printRow( course, column, user, member, score, dataOut );

      }// end for users
    } // end for columns
  } // end doMain()
  
  /* Convenience method to find a score for a column/membership in a list of scores. */
  private ScoreVO lookupScore( ColumnVO column, CourseMembershipVO member, ScoreVO[] scores )
  {
    ScoreVO returnValue = null;
    try {
      for ( ScoreVO scoreVO : scores ) {
        if ( null != scoreVO && column.getId().equals( scoreVO.getColumnId() ) ) {
          if ( member.getId().equals( scoreVO.getMemberId() ) ) {
            returnValue = scoreVO;
            break;
          }
        }
      }
    }
    catch ( Exception e ) {
      _errors.add( e.getMessage() );
      _LOG.error( e.getMessage(), e );
    }
    return returnValue;
  }

  /* Convenience method for getting an initialized client while optionally logging out an old one. */
  private WebServiceClient newWebServiceClient( WebServiceClient oldClient )
    throws RemoteException
  {
    if( null != oldClient ) {
      try {
        oldClient.logout();
      }
      catch( Exception e ) {
        _errors.add( e.getMessage() );
        _LOG.error( e.getMessage(), e );
      }
    }

    WebServiceClient newClient = new WebServiceClient( _appConfig );
    newClient.initialize();
    newClient.login();
    return newClient;
  }

  /* Sleep for the specified time, but don't throw exception if interrupted */
  private void quietSleep( long millis ) {
    try {
      Thread.sleep( millis );
    }
    catch( Exception e ) {
      _LOG.error( e.getMessage(), e );
      _errors.add( e.getMessage() );
    }
  }

  private void printHeader( PrintStream out ) {
    printRow( true, null, null, null, null, null, out );
  }

  private void printRow( CourseVO course, ColumnVO column, UserVO user, CourseMembershipVO member, ScoreVO score, PrintStream out ) {
    printRow( false, course, column, user, member, score, out );
  }

  private void printRow( boolean header, CourseVO course, ColumnVO column, UserVO user, CourseMembershipVO member, ScoreVO score, PrintStream out )
  {
    out.print( header ? "COURSE_ID"        : course.getCourseId() );
    out.print( _delimiter );
    out.print( header ? "COURSE_BATCHUID"  : course.getBatchUid() );
    out.print( _delimiter );
    out.print( header ? "COURSE_PKID"      : course.getId() );
    out.print( _delimiter );
    out.print( header ? "COURSE_TITLE"     : course.getName() );
    out.print( _delimiter );
    out.print( header ? "COURSE_TYPE"      : course.getCourseServiceLevel() );
    out.print( _delimiter );
    out.print( header ? "COURSE_AVAILABLE" : course.getAvailable() );
    out.print( _delimiter );

    out.print( header ? "COLUMN_NAME"            : column.getColumnDisplayName() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_PKID"            : column.getId() );
    out.print( _delimiter );
    out.print( header ? "IS_EXTERNAL_GRADE"      : (column.getExternalGrade() ? "Y" : "N") );
    out.print( _delimiter );
    out.print( header ? "COLUMN_IS_DELETED"      : (column.getDeleted() ? "Y" : "N") );
    out.print( _delimiter );
    out.print( header ? "COLUMN_PKID"            : column.getPosition() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_MODEL"           : column.getAggregationModel() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_CALC_TYPE"       : column.getCalculationType() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_DUE_DATE"        : column.getDueDate() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_MULTI_ATTEMPTS"  : column.getMultipleAttempts() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_POINTS_POSSIBLE" : column.getPossible() );
    out.print( _delimiter );
    out.print( header ? "COLUMN_IS_SCORABLE"     : (column.getScorable() ? "Y" : "N") );
    out.print( _delimiter );
    out.print( header ? "COLUMN_IS_VISIBLE"      : (column.getVisible() ? "Y" : "N") );
    out.print( _delimiter );

    out.print( header ? "USER_ID"           : user.getName() );
    out.print( _delimiter );
    out.print( header ? "USER_BATCHUID"     : user.getUserBatchUid() );
    out.print( _delimiter );
    out.print( header ? "USER_PKID"         : user.getId() );
    out.print( _delimiter );
    out.print( header ? "USER_IS_AVAILABLE" : (user.getIsAvailable() ? "Y" : "N") );
    out.print( _delimiter );
    out.print( header ? "USER_STUDENT_ID"   : user.getStudentId() );
    out.print( _delimiter );

    out.print( header ? "ENR_PKID"         : member.getId() );
    out.print( _delimiter );
    out.print( header ? "ENR_IS_AVAILABLE" : (member.getAvailable() ? "Y" : "N") );
    out.print( _delimiter );
    out.print( header ? "ENR_DATE"         : toDate(member.getEnrollmentDate()) );
    out.print( _delimiter );

    out.print( header ? "GRADE_DISPLAYED"    : (null == score ? "" : score.getSchemaGradeValue()) );
    out.print( _delimiter );
    out.print( header ? "GRADE"              : (null == score ? "" : score.getGrade()) );
    out.print( _delimiter );
    out.print( header ? "GRADE_ID"           : (null == score ? "" : score.getId()) );
    out.print( _delimiter );
    out.print( header ? "GRADE_MANUAL"       : (null == score ? "" : score.getManualGrade()) );
    out.print( _delimiter );
    out.print( header ? "GRADE_SCORE_MANUAL" : (null == score ? "" : score.getManualScore()) );
    out.print( _delimiter );
    out.print( header ? "GRADE_STATUS"       : (null == score ? "" : score.getStatus()) );

    out.println();
  }

  private final SimpleDateFormat _df = (new SimpleDateFormat( "yyyy/MM/dd HH:mm:ss" ));

  private String toDate( long seconds ) {
    // Date provided by web services is in seconds, we need to convert to
    // milliseconds and format to a String.  For more info, see:
    //   blackboard.platform.ws.WebserviceHelper.safeTime( Calendar c )
    return _df.format( new Date( (seconds * 1000) ) );
  }
}
