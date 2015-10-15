
package blackboard.ws.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import blackboard.ws.course.CourseWSStub.CourseVO;
import blackboard.ws.coursemembership.CourseMembershipWSStub.CourseMembershipVO;
import blackboard.ws.gradebook.GradebookWSStub.ColumnVO;
import blackboard.ws.gradebook.GradebookWSStub.ScoreVO;
import blackboard.ws.user.UserWSStub.UserVO;

/**
 * Simple course extract of grades that uses only the Blackboard-provided web
 * services.  Takes a configuration file and a single courseId as an argument
 * and loads data for that course.
 */
public class SampleExtractApp
{
  private static final org.apache.commons.logging.Log _LOG = //
    org.apache.commons.logging.LogFactory.getLog( SampleExtractApp.class );

  /**
   * @param args
   */
  public static void main( String[] args ) throws IOException
  {
    // validate the arguments
    if( null == args || args.length < 1 || args.length > 2 ) {
      throw new IllegalArgumentException("Usage: arg[0]=/path/to/application.properties, arg[1] = courseId to load (optional)");
    }

    File clientConfigFile = new File( args[ 0 ] );
    if ( !clientConfigFile.exists() ) {
      throw new IllegalArgumentException( "File [" + clientConfigFile + "] does not exist." );
    }

    Properties clientConfig = new Properties();
    _LOG.info( "Creating Web Service Client" );
    WebServiceClient wsClient = null;
    try
    {
      InputStream is = null;
      is = new BufferedInputStream( new FileInputStream( clientConfigFile ) );
      try {
        clientConfig.load( is );
        wsClient = new WebServiceClient( clientConfig );
        wsClient.initialize();
        wsClient.login();
      }
      finally {
        if ( null != is ) { is.close(); }
      }

      // Get the course to load.  If provided as a parameter, then that
      // overrides the value from the configuration.
      String courseToLoad;
      if( args.length > 1 ) {
        courseToLoad = args[1];
      }
      else {
        courseToLoad = clientConfig.getProperty( "sample.courseId" );
      }

      // Load course
      _LOG.info( "Loading course: " + courseToLoad );
      CourseVO course = wsClient.getCourseById( courseToLoad );
      if ( null == course ) {
        throw new IllegalArgumentException( "Course [" + courseToLoad + "] does not exist or could not be loaded." );
      }

      // Load users by course.  Note that this would included all users in the
      // the course, not just students.
      _LOG.info( " Loading users for course: " + course.getCourseId() );
      UserVO[] users = wsClient.getUsersByCoursePkId( course.getId() );
      _LOG.info( " Loaded [" + users.length + "] users for course: " + course.getCourseId() );
      for( UserVO user : users ) {
        _LOG.info( "  username=" + user.getName() + ", batchUid=" + user.getUserBatchUid() + ", Id=" + user.getId() );
      }

      // Load memberships JUST for students.
      _LOG.info( " Loading student memberships for course: " + course.getCourseId() );
      CourseMembershipVO[] students =
        wsClient.getMembershipsByCoursePkIdAndRoleIds( course.getId(), new String[] {"S"} );
      _LOG.info( " Loaded [" + students.length + "] student memberships for course: " + course.getCourseId() );
      for( CourseMembershipVO student : students ) {
        _LOG.info( "  membershipId=" + student.getId() + ", userId=" + student.getUserId() );
      }

      // Load course gradebook columns
      _LOG.info( " Loading columns for course: " + course.getCourseId() );
      ColumnVO[] columns = wsClient.getColumnsByCoursePkId( course.getId() );
      _LOG.info( " Loaded [" + columns.length + "] columns for course: " + course.getCourseId() );
      for( ColumnVO column : columns ) {
        _LOG.info( "  columnDisplayName=" + column.getColumnDisplayName() + ", columnId=" + column.getId() );
      }

      // Load scores (gradebook cells) for course
      _LOG.info( " Loading grades for course: " + course.getCourseId() );
      ScoreVO[] scores = wsClient.getScoresByCoursePkId( course.getId() );
      _LOG.info( " Loaded [" + scores.length + "] grades course: " + course.getCourseId() );
      for( ScoreVO score : scores ) {
        _LOG.info( "  grade=" + score.getSchemaGradeValue() + ", columnId=" + score.getColumnId() + ", memberId=" + score.getMemberId() );
      }
    }
    finally {
      if( null != wsClient ) { wsClient.logout(); }
      _LOG.info( "EXTRACT COMPLETE" );
    }
  } // end main()
}
