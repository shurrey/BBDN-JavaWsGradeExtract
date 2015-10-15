
package blackboard.ws.client;

import java.io.*;
import java.rmi.RemoteException;
import java.util.*;

import javax.security.auth.callback.*;

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.rampart.handler.WSSHandlerConstants;
import org.apache.rampart.handler.config.*;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.handler.WSHandlerConstants;

import blackboard.ws.context.ContextWSStub;
import blackboard.ws.context.ContextWSStub.ExtendSessionLife;
import blackboard.ws.course.CourseWSStub;
import blackboard.ws.course.CourseWSStub.CourseVO;
import blackboard.ws.course.CourseWSStub.GetCourse;
import blackboard.ws.course.CourseWSStub.InitializeCourseWS;
import blackboard.ws.coursemembership.CourseMembershipWSStub;
import blackboard.ws.coursemembership.CourseMembershipWSStub.CourseMembershipVO;
import blackboard.ws.coursemembership.CourseMembershipWSStub.GetCourseMembership;
import blackboard.ws.coursemembership.CourseMembershipWSStub.InitializeCourseMembershipWS;
import blackboard.ws.coursemembership.CourseMembershipWSStub.MembershipFilter;
import blackboard.ws.gradebook.GradebookWSStub;
import blackboard.ws.gradebook.GradebookWSStub.ColumnFilter;
import blackboard.ws.gradebook.GradebookWSStub.ColumnVO;
import blackboard.ws.gradebook.GradebookWSStub.GetGradebookColumns;
import blackboard.ws.gradebook.GradebookWSStub.GetGrades;
import blackboard.ws.gradebook.GradebookWSStub.InitializeGradebookWS;
import blackboard.ws.gradebook.GradebookWSStub.ScoreFilter;
import blackboard.ws.gradebook.GradebookWSStub.ScoreVO;
import blackboard.ws.user.UserWSStub;
import blackboard.ws.user.UserWSStub.GetUser;
import blackboard.ws.user.UserWSStub.InitializeUserWS;
import blackboard.ws.user.UserWSStub.UserFilter;
import blackboard.ws.user.UserWSStub.UserVO;
import blackboard.ws.util.UtilWSStub;
import blackboard.ws.util.UtilWSStub.InitializeUtilWS;

public class WebServiceClient {

    private static final org.apache.commons.logging.Log _LOG = //
      org.apache.commons.logging.LogFactory.getLog( WebServiceClient.class );

    private final static String LTI_RAW_USERNAME_PREFIX = "LTIRAW:";

    // no current functionality, reserved for future use
    private final String m_guid = null;

    // properties set on object construction
    private final String _protocol;
    private final String _hostname;
    private final String _toolProgramId;
    private final String _toolVendorId;
    private final String _toolPassword;
    private final String _toolDescription;
    private final String _toolRegistrationPassword;
    private final String _pathToAxisConf;
    private final String _pathToAxisLib;
    private final long _expectedLifeSeconds;
    private final long _extendSessionLifeSeconds;
    private final long _clientTimeoutMillis;

    // set on initialization
    private ContextWSStub _contextWS;
    private CourseWSStub _courseWS;
    private CourseMembershipWSStub _courseMembershipWS;
    private GradebookWSStub _gradebookWS;
    private UserWSStub _userWS;
    private UtilWSStub _utilWS;
    private long _contextServerVersion;
    private long _userServerVersion;
    private long _utilServerVersion;

    // state variables
    private String _sessionId = null;

    public WebServiceClient( Properties appConfig ) {
      this(
        appConfig.getProperty( "ws.protocol" ),
        appConfig.getProperty( "ws.hostname" ),
        appConfig.getProperty( "ws.toolVendorId" ),
        appConfig.getProperty( "ws.toolProgramId" ),
        appConfig.getProperty( "ws.toolPassword" ),
        appConfig.getProperty( "ws.toolDescription" ),
        appConfig.getProperty( "ws.toolRegistrationPassword" ),
        appConfig.getProperty( "ws.pathToAxisConf" ),
        appConfig.getProperty( "ws.pathToAxisLib" ),
        Long.valueOf(appConfig.getProperty( "ws.expectedLifeSeconds" )),
        Long.valueOf(appConfig.getProperty( "ws.extendSessionLifeSeconds", "-1" )),
        Long.valueOf(appConfig.getProperty( "ws.clientTimeoutMillis" ))
      );
    }
    
    public WebServiceClient(
      String protocol,
      String hostname,
      String toolVendorId,
      String toolProgramId,
      String toolPassword,
      String toolDescription,
      String toolRegistrationPassword,
      String pathToAxisConf,
      String pathToAxisLib,
      long expectedLifeSeconds,
      long extendSessionLifeSeconds,
      long clientTimeoutMillis
    ) {
      _protocol = protocol;
      _hostname = hostname;
      _toolVendorId = toolVendorId;
      _toolProgramId = toolProgramId;
      _toolPassword = toolPassword;
      _toolDescription = toolDescription;
      _toolRegistrationPassword = toolRegistrationPassword;
      _pathToAxisConf = pathToAxisConf;
      _pathToAxisLib = pathToAxisLib;
      _expectedLifeSeconds = expectedLifeSeconds;
      _extendSessionLifeSeconds = extendSessionLifeSeconds;
      _clientTimeoutMillis = clientTimeoutMillis;
    }

    public String getSessionId() {
        return _sessionId;
    }

    public String getPassword() {
        return _toolPassword;
    }

    public String getProgramId() {
        return _toolProgramId;
    }

    public String getVendorId() {
        return _toolVendorId;
    }

    public String getDescription() {
        return _toolDescription;
    }

    public String getRegistrationPassword() {
        return _toolRegistrationPassword;
    }

    public boolean useAutomaticLogin() {
        return m_guid != null;
    }

    public void initialize() throws RemoteException {

        _LOG.info("Initializing...");

        final String wsBaseURL = _protocol + "://" + _hostname + "/" + "/webapps/ws/services/";

        ConfigurationContext cfgCtx = ConfigurationContextFactory
          .createConfigurationContextFromFileSystem(_pathToAxisLib, _pathToAxisConf);

        _contextWS = new ContextWSStub(cfgCtx, wsBaseURL + "Context.WS");
        {
            ServiceClient client = _contextWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }
        _userWS = new UserWSStub(cfgCtx, wsBaseURL + "User.WS");
        {
            ServiceClient client = _userWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }
        _utilWS = new UtilWSStub(cfgCtx, wsBaseURL + "Util.WS");
        {
            ServiceClient client = _utilWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }
        _courseWS = new CourseWSStub(cfgCtx, wsBaseURL + "Course.WS");
        {
            ServiceClient client = _courseWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }
        _courseMembershipWS = new CourseMembershipWSStub(cfgCtx, wsBaseURL + "CourseMembership.WS");
        {
            ServiceClient client = _courseMembershipWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }
        _gradebookWS = new GradebookWSStub(cfgCtx, wsBaseURL + "Gradebook.WS");
        {
            ServiceClient client = _gradebookWS._getServiceClient();
            Options options = client.getOptions();
            setWebserviceClientOptions(options);
        }

        _LOG.info("Checking versions...");

        ContextWSStub.GetServerVersion cv = new ContextWSStub.GetServerVersion();
        _contextServerVersion = _contextWS.getServerVersion(cv).get_return().getVersion();
        _LOG.info("Context server version: " + _contextServerVersion);

        UserWSStub.GetServerVersion userv = new UserWSStub.GetServerVersion();
        _userServerVersion = _userWS.getServerVersion(userv).get_return().getVersion();
        _LOG.info("User server version: " + _userServerVersion);

        UtilWSStub.GetServerVersion uv = new UtilWSStub.GetServerVersion();
        _utilServerVersion = _utilWS.getServerVersion(uv).get_return().getVersion();
        _LOG.info("Util server version: " + _utilServerVersion);

        if (useAutomaticLogin()) {
            throw new IllegalStateException("\"Automatic Login\" not supported.");
        }

        _LOG.info("Initializing the session...");

        _sessionId = _contextWS.initialize().get_return();
        _LOG.info("Session Id: " + _sessionId);

        _LOG.info("Initializing Util Context...");
        {
            InitializeUtilWS initUtil = new InitializeUtilWS();
            initUtil.setIgnore(true);
            _utilWS.initializeUtilWS(initUtil).get_return();
        }

        _LOG.info("Initializing User Context...");
        {
            InitializeUserWS initUser = new InitializeUserWS();
            initUser.setIgnore(true);
            _userWS.initializeUserWS(initUser).get_return();
        }

        _LOG.info("Initializing Course Context...");
        {
            InitializeCourseWS initCourse = new InitializeCourseWS();
            initCourse.setIgnore(true);
            _courseWS.initializeCourseWS(initCourse).get_return();
        }

        _LOG.info("Initializing Course Membership Context...");
        {
            InitializeCourseMembershipWS initCourseMembership = new InitializeCourseMembershipWS();
            initCourseMembership.setIgnore(true);
            _courseMembershipWS.initializeCourseMembershipWS(initCourseMembership).get_return();
        }

        _LOG.info("Initializing Gradebook Context...");
        {
            InitializeGradebookWS initGradebook = new InitializeGradebookWS();
            initGradebook.setIgnore(true);
            _gradebookWS.initializeGradebookWS(initGradebook).get_return();
        }

        // register the tool
        _LOG.info("Registering Tool...");
        ContextWSStub.RegisterTool rt = new ContextWSStub.RegisterTool();
        rt.setClientProgramId(getProgramId());
        rt.setClientVendorId(getVendorId());
        rt.setDescription(getDescription());
        rt.setInitialSharedSecret((getPassword() == null) ? "" : getPassword());
        rt.setRegistrationPassword(getRegistrationPassword());
        rt.setRequiredTicketMethods(null);
        rt.setRequiredToolMethods(_TOOL_METHODS);
        ContextWSStub.RegisterToolResultVO toolRegResult = _contextWS.registerTool(rt).get_return();
//        _LOG.info("ProxyTool GUID: " + String.valueOf( toolRegResult.getProxyToolGuid() ) );

        if ( !toolRegResult.getStatus() ) {
            _LOG.warn( "Errors/warnings registering tool: " );
            if ( toolRegResult.getFailureErrors() != null ) {
                for ( String s : toolRegResult.getFailureErrors() ) {
                    _LOG.warn(" - " + s);
                }
            }
        }

        _LOG.info("Initialization complete.");
    }

    public boolean login() throws RemoteException
    {
      if ( getSessionId() == null ) {
        throw new IllegalStateException( "Attempted login without a session.  Must initialize() client first." );
      }
      ContextWSStub.LoginTool loginArgs = new ContextWSStub.LoginTool();
      loginArgs.setPassword( _toolPassword );
      loginArgs.setClientVendorId( getVendorId() );
      loginArgs.setClientProgramId( getProgramId() );
      loginArgs.setLoginExtraInfo( "" );
      loginArgs.setExpectedLifeSeconds( _expectedLifeSeconds );

      ContextWSStub.LoginToolResponse loginResult = _contextWS.loginTool( loginArgs );
      
      // Set the extended session life seconds (if provided)
      if ( _extendSessionLifeSeconds > 0 ) {
        ExtendSessionLife eslParam = new ExtendSessionLife();
        eslParam.setAdditionalSeconds( _extendSessionLifeSeconds );
        _contextWS.extendSessionLife( eslParam );
      }

      return loginResult.get_return();
    }

    public boolean logout() throws RemoteException {
      if ( null != _contextWS ) {
        return _contextWS.logout().get_return();
      }
      return false;
    }

    /*
     * Course Load Methods
     * 
     * For information on constants for filter types, see Javadoc for
     * Blackboard's platform API (bb-platform.jar) class:
     *   blackboard.ws.course.CourseWSConstants
     *     public static final int GET_ALL_COURSES = 0;
     *     public static final int GET_COURSE_BY_COURSEID = 1;
     *     public static final int GET_COURSE_BY_BATCHUID = 2;
     *     public static final int GET_COURSE_BY_ID = 3;
     *     public static final int GET_COURSE_BY_CATEGORY_ID = 4;
     *     public static final int GET_COURSE_BY_SEARCH_KEYVALUE = 5;
     * 
     * For information on how these filters are applied (i.e. which methods are
     * invoked for which filter constants), see Javadoc for Blackboard's
     * platform API (bb-platform.jar) class:
     *   blackboard.ws.course.CourseFilter
     */

    public CourseVO[] getAllCourses() throws RemoteException
    {
      GetCourse getCourseParam = new GetCourse();
      CourseWSStub.CourseFilter filter = new CourseWSStub.CourseFilter();
      filter.setFilterType( 0 ); // 0 = All Courses
      getCourseParam.setFilter( filter );
      CourseVO[] results = _courseWS.getCourse( getCourseParam ).get_return();
      if( null == results ) {
        results = new CourseVO[0];
      }
      return results;
    }

    /* 
     * Example: getCoursesBySearch("CourseId", "Contains", "someValueToSearchOn")
     * see: blackboard.ws.course.CourseFilter.java, blackboard.ws.course.CourseWSConstants.java
     */
    public CourseVO[] getCoursesBySearch(String searchKey, String searchOperator, String searchValue ) throws RemoteException
    {
      GetCourse getCourseParam = new GetCourse();
      CourseWSStub.CourseFilter filter = new CourseWSStub.CourseFilter();
      filter.setFilterType( 5 ); // 5 = search
      filter.setSearchDate( 0l );
      filter.setSearchDateOperator( "GreaterThan" );
      filter.setSearchOperator( searchOperator );
      filter.setSearchKey( searchKey );
      filter.setSearchValue( searchValue );
      getCourseParam.setFilter( filter );
      CourseVO[] results = _courseWS.getCourse( getCourseParam ).get_return();
      if( null == results ) {
        results = new CourseVO[0];
      }
      return results;
    }

    public CourseVO getCourseByCourseId( String courseId ) throws RemoteException
    {
      if( null == courseId || courseId.trim().length() == 0 ) {
        throw new IllegalArgumentException("Parameter courseId cannot be null or empty.");
      }
      GetCourse getCourseParam = new GetCourse();
      CourseWSStub.CourseFilter filter = new CourseWSStub.CourseFilter();
      filter.setFilterType( 1 ); // 1 = Course by Id(s)
      filter.setCourseIds( new String[] { courseId } );
      getCourseParam.setFilter( filter );

      CourseVO[] courses = _courseWS.getCourse( getCourseParam ).get_return();
      if ( null != courses && courses.length > 1 ) {
        String errMsg = "getCourseByCourseId returned " + courses.length + " results and should only return 1 or 0.  Matches: ";
        for ( int ii = 0; ii < courses.length; ii++ ) {
          errMsg += courses[ ii ].getCourseId();
          if ( ii < ( courses.length - 1 ) ) {
            errMsg += ", ";
          }
        }

        throw new IllegalStateException( errMsg );
      }

      return (null == courses) ? null : courses[ 0 ];
    }

    public CourseVO getCourseById( String id ) throws RemoteException
    {
      if( null == id || id.trim().length() == 0 ) {
        throw new IllegalArgumentException("Parameter id cannot be null or empty.");
      }
      GetCourse getCourseParam = new GetCourse();
      CourseWSStub.CourseFilter filter = new CourseWSStub.CourseFilter();
      filter.setFilterType( 3 ); // 1 = Course by Id(s)
      filter.setIds( new String[] { id } );
      getCourseParam.setFilter( filter );

      CourseVO[] courses = _courseWS.getCourse( getCourseParam ).get_return();
      if ( null != courses && courses.length > 1 ) {
        String errMsg = "getCourseByCourseId returned " + courses.length + " results and should only return 1 or 0.  Matches: ";
        for ( int ii = 0; ii < courses.length; ii++ ) {
          errMsg += courses[ ii ].getCourseId();
          if ( ii < ( courses.length - 1 ) ) {
            errMsg += ", ";
          }
        }

        throw new IllegalStateException( errMsg );
      }

      return (null == courses) ? null : courses[ 0 ];
    }

    /*
     * User Load Methods
     * 
     * For information on constants for filter types, see Javadoc for
     * Blackboard's platform API (bb-platform.jar) class:
     *   blackboard.ws.user.UserWSConstants
     *     * Use filter.filterType and filter.available
     *     public static final int GET_ALL_USERS_WITH_AVAILABILITY = 1;
     *     * Use filter.id and filter.available
     *     public static final int GET_USER_BY_ID_WITH_AVAILABILITY = 2;
     *     *  Use filter.batchid and filter.available
     *     public static final int GET_USER_BY_BATCH_ID_WITH_AVAILABILITY = 3;
     *     *  Use filter.courseid and filter.available
     *     public static final int GET_USER_BY_COURSE_ID_WITH_AVAILABILITY = 4;
     *     *  Use filter.groupid and filter.available
     *     public static final int GET_USER_BY_GROUP_ID_WITH_AVAILABILITY = 5;
     *     *  Use filter.name and filter.available
     *     public static final int GET_USER_BY_NAME_WITH_AVAILABILITY = 6;
     *     *  Use filter.systemroles and filter.available
     *     public static final int GET_USER_BY_SYSTEM_ROLE = 7;
     *     *  Use filter.id and filter.available
     *     public static final int GET_ADDRESS_BOOK_ENTRY_BY_ID = 8;
     *     *  Use filter.filterType and filter.available
     *     *  Note: This filter type is applicable only for user bases authentication. It is not applicable for tool login.
     *     public static final int GET_ADDRESS_BOOK_ENTRY_BY_CURRENT_USERID = 9;
     * 
     * For information on how these filters are applied (i.e. which methods are
     * invoked for which filter constants), see Javadoc for Blackboard's
     * platform API (bb-platform.jar) class:
     *   blackboard.ws.user.UserFilter
     */

    /* Load users by coursePkId, i.e. _X_1.  If none found, empty array returned. */
    public UserVO[] getUsersByCoursePkId( String coursePkId ) throws RemoteException
    {
      GetUser userParam = new GetUser();
      UserFilter userFilter = new UserFilter();
      userFilter.setFilterType( 4 ); // 4 = Load by course Id, 2 = byId
      userFilter.setCourseId( new String[] { coursePkId } );
      userParam.setFilter( userFilter );
      UserVO[] users = _userWS.getUser( userParam ).get_return();
      if ( null == users ) {
        users = new UserVO[ 0 ];
      }
      return users;
    }

    /*
     * Course Membership Load Methods
     * 
     * For information on constants for filter types, see Javadoc for
     * Blackboard's platform API (bb-platform.jar) class:
     *   blackboard.ws.coursemembership.CourseMembershipWSConstants
     *     public static final int BY_ID = 1;
     *     public static final int BY_CRS_ID = 2;
     *     public static final int BY_CRS_MEM_ID = 3;
     *     public static final int BY_GRP_ID = 4;
     *     public static final int BY_USER_ID = 5;
     *     public static final int BY_CRS_ID_AND_USER_ID = 6;
     *     public static final int BY_CRS_ID_AND_ROLE_ID = 7;
     * 
     * For information on how these filters are applied (i.e. which methods are
     * invoked for which filter constants), see Javadoc for Blackboard's
     * platform API (bb-platform.jar) class:
     *   blackboard.ws.coursemembership.CourseMembershipFilter
     */
    
    /* Load users by coursePkId, i.e. _X_1.  If none found, empty array returned. */
    public CourseMembershipVO[] getMembershipsByCoursePkIdAndRoleIds( String coursePkId, String[] roleIds ) throws RemoteException
    {
      GetCourseMembership param = new GetCourseMembership();
      MembershipFilter filter = new MembershipFilter();
      filter.setFilterType( 7 ); // 2 = by courseId, 7 = by courseIdAndRole
      filter.setCourseIds( new String[] { coursePkId } );
      filter.setRoleIds( roleIds );
      param.setF( filter );
      param.setCourseId( coursePkId );
      CourseMembershipVO[] results = _courseMembershipWS.getCourseMembership( param ).get_return();
      if ( null == results ) {
        results = new CourseMembershipVO[ 0 ];
      }
      return results;
    }

    /*
     * Gradebook Load Methods
     * 
     * For information on constants for filter types, see Javadoc for
     * Blackboard's platform API (bb-platform.jar) class:
     *   blackboard.ws.gradebook.GradebookWSConstants
     *     ** Supported filter type values which must be specified for loading grades
     *     public static final int GET_SCORE_BY_COURSE_ID = 1;
     *     public static final int GET_SCORE_BY_COLUMN_ID_AND_USER_ID = 2;
     *     public static final int GET_SCORE_BY_COLUMN_ID = 3;
     *     // final grade is also know is external grade
     *     public static final int GET_SCORE_BY_COURSE_ID_AND_FINAL_GRADE = 4;
     *     public static final int GET_SCORE_BY_MEMBER_ID_AND_COLUMN_ID = 5;
     *     public static final int GET_SCORE_BY_MEMBER_ID = 6;
     *     public static final int GET_SCORE_BY_ID = 7;
     *     public static final int GET_SCORE_BY_USER_ID = 8;
     *     public static final int GET_SCORE_BY_USER_ID_AND_FINAL_GRADE = 9;
     *     public static final int GET_SCORE_BY_MEMBER_IDS_AND_COLUMN_ID = 10;
     *     ** Supported filter type values which must be specified for loading attempts
     *     public static final int GET_ATTEMPT_BY_GRADE_ID = 1;
     *     public static final int GET_ATTEMPT_BY_GRADE_ID_AND_LAST_ATTEMPT = 2;
     *     public static final int GET_ATTEMPT_BY_IDS = 3;
     *     ** Supported filter type values which must be specified for loading columns
     *      * For GET_BY_COURSE_ID, framework will pass in the course id.  So, no course id need to be set in the filter.
     *     public static final int GET_COLUMN_BY_COURSE_ID = 1;
     *      * For GET_BY_COURSE_ID_AND_COLUMN_NAME, courseId and name must be populated
     *     public static final int GET_COLUMN_BY_COURSE_ID_AND_COLUMN_NAME = 2;
     *      * For GET_BY_ID, id(s) must be populated
     *     public static final int GET_COLUMN_BY_IDS = 3;
     *      * For GET_BY_EXTERNAL_GRADE_FLAG, courseId must be populated
     *     public static final int GET_COLUMN_BY_EXTERNAL_GRADE_FLAG = 4;
     * 
     * For information on how these filters are applied (i.e. which methods are
     * invoked for which filter constants), see Javadoc for Blackboard's
     * platform API (bb-platform.jar) class:
     *   blackboard.ws.gradebook.AttemptFilter
     *   blackboard.ws.gradebook.ColumnFilter
     *   blackboard.ws.gradebook.ScoreFilter
     */

    public ScoreVO[] getScoresByCoursePkId( String coursePkId ) throws RemoteException
    {
      GetGrades param = new GetGrades();
      param.setCourseId( coursePkId );
      ScoreFilter filter = new ScoreFilter();
//      filter.setId( coursePkId );
      filter.setFilterType( 1 ); // 1 = by courseId, 5 = by courseId, columnId, and membershipId
      param.setFilter( filter );
      ScoreVO[] results = _gradebookWS.getGrades( param ).get_return();
      if( null == results ) {
        results = new ScoreVO[0];
      }
      return results;
    }

    public ColumnVO getExternalGradeColumnByCoursePkId( String coursePkId ) throws RemoteException
    {
      ColumnVO[] results = getColumnsByCoursePkId( coursePkId, 4 );
      if ( null != results && results.length > 1 ) {
        String errMsg = "getExternalGradeColumnByCoursePkId returned " + results.length + " results and should only return 1 or 0.  Matches: ";
        for ( int ii = 0; ii < results.length; ii++ ) {
          errMsg += results[ ii ].getColumnDisplayName();
          if ( ii < ( results.length - 1 ) ) {
            errMsg += ", ";
          }
        }

        throw new IllegalStateException( errMsg );
      }
      return (null == results) ? null : results[0];
    }

    public ColumnVO[] getColumnsByCoursePkId( String coursePkId ) throws RemoteException {
      return getColumnsByCoursePkId( coursePkId, 1 );
    }

    private ColumnVO[] getColumnsByCoursePkId( String coursePkId, int filterType ) throws RemoteException
    {
      GetGradebookColumns param = new GetGradebookColumns();
      param.setCourseId( coursePkId );
      ColumnFilter filter = new ColumnFilter();
      filter.setFilterType( filterType ); // 1 = all columns by course, 4 = column by external_grade, 
      param.setFilter( filter );
      ColumnVO[] columns = _gradebookWS.getGradebookColumns( param ).get_return();
      if ( null == columns ) {
        columns = new ColumnVO[ 0 ];
      }
      return columns;
    }

    /* internal method to set appropriate configuration on ws client */
    private void setWebserviceClientOptions( Options op )
    {
        /* NOTE that the following options-setting code to force the use of  the 1.0 protocol
         * is here to get around what appears to be a tomcat+axis2+iis issue where the ajp connection
         * between iis and tomcat will attempt to continue to read data and fail when the axis2 client
         * is using http/1.1 as the protocol.
         */
        op.setProperty(HTTPConstants.HTTP_PROTOCOL_VERSION, HTTPConstants.HEADER_PROTOCOL_10);

        // Set the callback handler
        op.setProperty(WSHandlerConstants.PW_CALLBACK_REF,
          new CallbackHandler() {
            public void handle( Callback[] callbacks ) throws IOException, UnsupportedCallbackException {
              for (int i = 0; i < callbacks.length; i++) {
                WSPasswordCallback pwcb = (WSPasswordCallback) callbacks[i];
                if (useAutomaticLogin()) {
                  pwcb.setPassword(getPassword());
                }
                else if (getSessionId() != null) {
                  pwcb.setPassword(getSessionId());
                }
                else {
                  pwcb.setPassword("nosession");
                }
              }
            } // end handle()
          }// end CallbackHandler()
        );

        // Set the security settings
        OutflowConfiguration ofc = new OutflowConfiguration();
        ofc.setActionItems("UsernameToken Timestamp");
        if (useAutomaticLogin()) {
            ofc.setUser(LTI_RAW_USERNAME_PREFIX + m_guid);
        } else {
            ofc.setUser("session");
        }
        ofc.setPasswordType("PasswordText");
        op.setProperty(WSSHandlerConstants.OUTFLOW_SECURITY, ofc.getProperty());
        op.setTimeOutInMilliSeconds(_clientTimeoutMillis);
    }

    private static final String [] _TOOL_METHODS = {
        "Context.WS:emulateUser", 
        "Context.WS:logout", 
        "Context.WS:getMemberships", 
        "Context.WS:getMyMemberships",
        "Util.WS:checkEntitlement", 
        "User.WS:getUser",
        "User.WS:getServerVersion",
        "User.WS:initializeUserWS", 
        "CourseMembership.WS:deleteCourseMembership", 
        "CourseMembership.WS:deleteGroupMembership",
        "CourseMembership.WS:getCourseMembership", 
        "CourseMembership.WS:getCourseRoles",
        "CourseMembership.WS:getGroupMembership", 
        "CourseMembership.WS:getServerVersion",
        "CourseMembership.WS:initializeCourseMembershipWS", 
        "CourseMembership.WS:saveCourseMembership",
        "CourseMembership.WS:saveGroupMembership",
        "Course.WS:changeCourseCategoryBatchUid",
        "Course.WS:changeCourseBatchUid",
        "Course.WS:changeCourseDataSourceId",
        "Course.WS:createCourse",
        "Course.WS:createOrg",
        "Course.WS:deleteCartridge",
        "Course.WS:deleteCourse",
        "Course.WS:deleteCourseCategory",
        "Course.WS:deleteCourseCategoryMembership",
        "Course.WS:deleteGroup",
        "Course.WS:deleteOrg",
        "Course.WS:deleteOrgCategory",
        "Course.WS:deleteOrgCategoryMembership",
        "Course.WS:deleteStaffInfo",
        "Course.WS:getAvailableGroupTools",
        "Course.WS:getCartridge",
        "Course.WS:getCategories",
        "Course.WS:getCategoryMembership",
        "Course.WS:getClassifications",
        "Course.WS:getCourse",
        "Course.WS:getGroup",
        "Course.WS:getOrg",
        "Course.WS:getStaffInfo",
        "Course.WS:initializeCourseWS",
        "Course.WS:saveCartridge",
        "Course.WS:saveCourse",
        "Course.WS:saveCourseCategory",
        "Course.WS:saveCourseCategoryMembership",
        "Course.WS:saveGroup",
        "Course.WS:saveOrgCategory",
        "Course.WS:saveOrgCategoryMembership",
        "Course.WS:saveStaffInfo",
        "Course.WS:updateCourse",
        "Course.WS:updateOrg",
        "Gradebook.WS:deleteAttempts",
        "Gradebook.WS:deleteColumn",
        "Gradebook.WS:deleteGradebookTypes",
        "Gradebook.WS:deleteGrades",
        "Gradebook.WS:deleteGradingSchemas",
        "Gradebook.WS:getAttempts",
        "Gradebook.WS:getGradebookColumns",
        "Gradebook.WS:getGradebookTypes",
        "Gradebook.WS:getGrades",
        "Gradebook.WS:getGradingSchemas",
        "Gradebook.WS:getRequiredEntitlements",
        "Gradebook.WS:getServerVersion",
        "Gradebook.WS:initializeGradebookWS",
        "Gradebook.WS:saveAttempts",
        "Gradebook.WS:saveColumns",
        "Gradebook.WS:saveGradebookTypes",
        "Gradebook.WS:saveGrades",
        "Gradebook.WS:saveGradingSchemas",
        "Gradebook.WS:updateColumnAttribute"
    };
}
