package com.veracode.verademo.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.veracode.verademo.model.Blabber;
import com.veracode.verademo.utils.Constants;
import com.veracode.verademo.utils.User;
import com.veracode.verademo.utils.UserFactory;

/**
 * @author johnadmin
 */
@Controller
@Scope("request")
public class UserController {
	private static final Logger logger = LogManager.getLogger("VeraDemo:UserController");

	@Autowired
	ServletContext context;
	
	/**
	 * @param target
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String showLogin(@RequestParam(value = "target", required = false) String target,
							@RequestParam(value = "username", required=false) String username,
							Model model,
							HttpServletRequest httpRequest,
							HttpServletResponse httpResponse)
	{
		// Check if user is already logged in
		if (httpRequest.getSession().getAttribute("username") != null) {
			logger.info("User is already logged in - redirecting...");
			if (target != null && !target.isEmpty() && !target.equals("null")) {
				return "redirect:" + target;
			} else {
				// default to user's feed
				return "redirect:feed";
			}
		}
		
		User user = UserFactory.createFromRequest(httpRequest);
		if (user != null) {
			httpRequest.getSession().setAttribute("username", user.getUserName());
			logger.info("User is remembered - redirecting...");
			if (target != null && !target.isEmpty() && !target.equals("null")) {
				return "redirect:" + target;
			} else {
				// default to user's feed
				return "redirect:feed";
			}
		} else {
			logger.info("User is not remembered");
		}
		
		if (username == null) {
			username = "";
		}
		
		if (target == null) {
			target = "";
		}
		
		logger.info("Entering showLogin with username " + username + " and target " + target);
		
		model.addAttribute("username", username);
		model.addAttribute("target", target);
		return "login";
	}

	/**
	 * @param username
	 * @param password
	 * @param target
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public String processLogin(@RequestParam(value = "user", required = true) String username,
							   @RequestParam(value = "password", required = true) String password,
							   @RequestParam(value = "remember", required = false) String remember,
							   @RequestParam(value = "target", required = false) String target,
							   Model model,
							   HttpServletRequest req,
							   HttpServletResponse response) {
		logger.info("Entering processLogin");

		// Determine eventual redirect. Do this here in case we're already logged in
		String nextView;
		if (target != null && !target.isEmpty() && !target.equals("null")) {
			nextView = "redirect:" + target;
		} else {
			// default to user's feed
			nextView = "redirect:feed";
		}
		
		Connection connect = null;
		Statement sqlStatement = null;

		try {
			// Get the Database Connection
			logger.info("Creating the Database connection");
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());

			/* START BAD CODE */
			// Execute the query
			logger.info("Creating the Statement");
			String sqlQuery = "select username, password, created_at, last_login, real_name, blab_name from users where username='" + username + "' and password='" + password + "';";
			sqlStatement = connect.createStatement();
			logger.info("Execute the Statement");
			ResultSet result = sqlStatement.executeQuery(sqlQuery);
			/* END BAD CODE */

			// Did we find exactly 1 user that matched?
			if (result.first()) {
				logger.info("User Found.");
				// Remember the username as a courtesy.
				response.addCookie(new Cookie("username", username));
				
				// If the user wants us to auto-login, store the user details as a cookie.
				if (remember != null) {
					User currentUser = new User(
							result.getString("username"),
							result.getString("password"),
							result.getTimestamp("created_at"),
							result.getTimestamp("last_login"),
							result.getString("real_name"),
							result.getString("blab_name")
					);
					
					UserFactory.updateInResponse(currentUser, response);
				}
				
				req.getSession().setAttribute("username", username);
			}
			else {
				// Login failed...
				logger.info("User Not Found");
				model.addAttribute("error", "Login failed. Please try again.");
				model.addAttribute("target", target);
				nextView = "login";
			}
		}
		catch (SQLException exceptSql) {
			logger.error(exceptSql);
			model.addAttribute("error", exceptSql.getMessage() + "<br/>" + displayErrorForWeb(exceptSql));
			model.addAttribute("target", target);
		}
		catch (ClassNotFoundException cnfe) {
			logger.error(cnfe);
			model.addAttribute("error", cnfe.getMessage());
			model.addAttribute("target", target);

		}
		finally {
			try {
				if (sqlStatement != null) {
					sqlStatement.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
				model.addAttribute("error", exceptSql.getMessage());
				model.addAttribute("target", target);
			}
			try {
				if (connect != null) {
					connect.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
				model.addAttribute("error", exceptSql.getMessage());
				model.addAttribute("target", target);
			}
		}
		
		// Redirect to the appropriate place based on login actions above
		logger.info("Redirecting to view: " + nextView);
		return nextView;
	}

	@RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
	public String processLogout(
			@RequestParam(value = "type", required = false) String type, 
			Model model,
			HttpServletRequest req,
			HttpServletResponse response
		) {
		logger.info("Entering processLogout");
		
		req.getSession().setAttribute("username", null);
		
		User currentUser = null;
		UserFactory.updateInResponse(currentUser, response);
		logger.info("Redirecting to Login...");
		return "redirect:login";
	}

	@RequestMapping(value = "/register", method = RequestMethod.GET)
	public String showRegister() {
		logger.info("Entering showRegister");

		return "register";
	}
	
	@RequestMapping(value = "/register", method = RequestMethod.POST)
	public String processRegister(
			@RequestParam(value = "user") String username, 
			HttpServletRequest httpRequest,
			Model model)
	{
		logger.info("Entering processRegister");
		
		// Get the Database Connection
		logger.info("Creating the Database connection");
		try {
			Class.forName("com.mysql.jdbc.Driver");
			Connection connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());
			
			String sql = "SELECT username FROM users WHERE username = '" + username + "'";
			Statement statement = connect.createStatement();
			ResultSet result = statement.executeQuery(sql);
			if (result.first()) {
				model.addAttribute("error", "Username '" + username + "' already exists!");
				return "register";
			}
			else {
				httpRequest.getSession().setAttribute("username", username);
				return "register-finish";
			}
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		
		return "register";
	}
	
	
	@RequestMapping(value = "/register-finish", method = RequestMethod.GET)
	public String showRegisterFinish() {
		logger.info("Entering showRegisterFinish");
		
		return "register-finish";
	}

	@RequestMapping(value = "/register-finish", method = RequestMethod.POST)
	public String processRegisterFinish(
								  @RequestParam(value = "password", required = true) String password,
								  @RequestParam(value = "cpassword", required = true) String cpassword,
								  @RequestParam(value = "realName", required = true) String realName,
								  @RequestParam(value = "blabName", required = true) String blabName,
								  HttpServletRequest httpRequest,
								  HttpServletResponse response,
								  Model model)
	{
		logger.info("Entering processRegisterFinish");
		
		String username = (String) httpRequest.getSession().getAttribute("username");

		// Do the password and cpassword parameters match ?
		if (password.compareTo(cpassword) != 0) {
			logger.info("Password and Confirm Password do not match");
			model.addAttribute("error", "The Password and Confirm Password values do not match. Please try again.");
			return "register";
		}
		
		Connection connect = null;
		Statement sqlStatement = null;

		try {
			// Get the Database Connection
			logger.info("Creating the Database connection");
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());

			/* START BAD CODE */
			// Execute the query
			String mysqlCurrentDateTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(Calendar.getInstance().getTime());
			StringBuilder query = new StringBuilder();
			query.append("insert into users (username, password, created_at, real_name, blab_name) values(");
			query.append("'" + username + "',");
			query.append("'" + password + "',");
			query.append("'" + mysqlCurrentDateTime + "',");
			query.append("'" + realName + "',");
			query.append("'" + blabName + "'");
			query.append(");");
			
			sqlStatement = connect.createStatement();
			sqlStatement.execute(query.toString());
			/* END BAD CODE */
			emailUser(username);
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		finally {
			try {
				if (sqlStatement != null) {
					sqlStatement.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
			try {
				if (connect != null) {
					connect.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
		}
		return "redirect:login?username=" + username;
	}

	private void emailUser(String username) {
		String to = "admin@example.com";
		String from = "verademo@veracode.com";
		String host = "localhost";
		String port = "5555";

		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);

		Session session = Session.getDefaultInstance(properties);

		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			
			/* START BAD CODE */
			message.setSubject("New user registered: " + username);
			/* END BAD CODE */
			
			message.setText("A new VeraDemo user registered: " + username);

			logger.info("Sending email to admin");
			Transport.send(message);
		}
		catch (MessagingException mex) {
			mex.printStackTrace();
		}
	}

	
	public class Test {
		private String foo;
		public Test() {
			this.foo = "bar";
		}
		public String getFoo() {
			return this.foo;
		}
	}
	
	@RequestMapping(value = "/profile", method = RequestMethod.GET)
	public String showProfile(
			@RequestParam(value = "type", required = false) String type, 
			Model model,
			HttpServletRequest httpRequest)
	{
		logger.info("Entering showProfile");
		
		String username = (String) httpRequest.getSession().getAttribute("username");
		// Ensure user is logged in
		if (username == null) {
			logger.info("User is not Logged In - redirecting...");
			return "redirect:login?target=profile";
		}
		
		Connection connect = null;
		PreparedStatement myHecklers = null, myInfo = null;
		String sqlMyHecklers = "SELECT users.username, users.blab_name, users.created_at "
				+ "FROM users LEFT JOIN listeners ON users.username = listeners.listener "
				+ "WHERE listeners.blabber=? AND listeners.status='Active';";
		
		try {
			logger.info("Getting Database connection");
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());

			// Find the Blabbers that this user listens to
			logger.info(sqlMyHecklers);
			myHecklers = connect.prepareStatement(sqlMyHecklers);
			myHecklers.setString(1, username);
			ResultSet myHecklersResults = myHecklers.executeQuery();
			
			List<Blabber> hecklers = new ArrayList<Blabber>();
			while (myHecklersResults.next()) {
				Blabber heckler = new Blabber();
				heckler.setUsername(myHecklersResults.getString(1));
				heckler.setBlabName(myHecklersResults.getString(2));
				heckler.setCreatedDate(myHecklersResults.getDate(3));
				hecklers.add(heckler);
			}
			
			// Get the audit trail for this user
			ArrayList<String> events = new ArrayList<String>();
			
			/* START BAD CODE */
			String sqlMyEvents = "select event from users_history where blabber=\"" + username + "\" ORDER BY eventid DESC; ";
			logger.info(sqlMyEvents);
			Statement sqlStatement = connect.createStatement();
			ResultSet userHistoryResult = sqlStatement.executeQuery(sqlMyEvents);
			/* END BAD CODE */
			
			while (userHistoryResult.next()) {
				events.add(userHistoryResult.getString(1));
			}
			
			//Get the users information
			String sql = "SELECT username, real_name, blab_name FROM users WHERE username = '" + username + "'";
			logger.info(sql);
			myInfo = connect.prepareStatement(sql);
			ResultSet myInfoResults = myInfo.executeQuery();
			myInfoResults.next();

			// Send these values to our View
			model.addAttribute("hecklers", hecklers);
			model.addAttribute("events", events);
			model.addAttribute("username", myInfoResults.getString("username"));
			model.addAttribute("realName", myInfoResults.getString("real_name"));
			model.addAttribute("blabName", myInfoResults.getString("blab_name"));
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		finally {
			try {
				if (myHecklers != null) {
					myHecklers.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
			try {
				if (connect != null) {
					connect.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
		}

		return "profile";
	}

	@RequestMapping(value = "/profile", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public String processProfile(@RequestParam(value = "realName", required = true) String realName,
								 @RequestParam(value = "blabName", required = true) String blabName,
								 @RequestParam(value = "username", required = true) String username,
								 @RequestParam(value = "file", required = false) MultipartFile file,
								 MultipartHttpServletRequest request,
								 HttpServletResponse response)
	{
		logger.info("Entering processProfile");
		
		String sessionUsername = (String) request.getSession().getAttribute("username");
		// Ensure user is logged in
		if (sessionUsername == null) {
			logger.info("User is not Logged In - redirecting...");
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return "{\"message\": \"<script>alert('Error - please login');</script>\"}";
		}
		
		logger.info("User is Logged In - continuing...");

		String oldUsername = sessionUsername;
		
		// Update user information
		Connection connect = null;
		PreparedStatement update = null;
		try {
			logger.info("Getting Database connection");
			// Get the Database Connection
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());

			//
			logger.info("Preparing the update Prepared Statement");
			update = connect.prepareStatement("UPDATE users SET real_name=?, blab_name=? WHERE username=?;");
			update.setString(1, realName);
			update.setString(2, blabName);
			update.setString(3, sessionUsername);

			logger.info("Executing the update Prepared Statement");
			boolean updateResult = update.execute();

			// If there is a record...
			if (updateResult) {
				//failure
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return "{\"message\": \"<script>alert('An error occurred, please try again.');</script>\"}";
			}
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		finally {
			try {
				if (update != null) {
					update.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
			try {
				if (connect != null) {
					connect.close();
				}
			}
			catch (SQLException exceptSql) {
				logger.error(exceptSql);
			}
		}
		
		// Rename profile image if username changes
		if (!username.equals(oldUsername)) {
			if (usernameExists(username)) {
				response.setStatus(HttpServletResponse.SC_CONFLICT);
				return "{\"message\": \"<script>alert('That username already exists. Please try another.');</script>\"}"; 
			}
			
			if(!updateUsername(oldUsername, username)) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return "{\"message\": \"<script>alert('An error occurred, please try again.');</script>\"}";
			}
			
			// Update all session and cookie logic
			request.getSession().setAttribute("username", username);
			for (Cookie cookie : request.getCookies()) {
				if (cookie.getName().equals("username")) {
					cookie.setValue(username);
					response.addCookie(cookie);
				}
			}
			
			// Update remember me functionality
			User currentUser = UserFactory.createFromRequest(request);
			if (currentUser != null) {
				currentUser.setUserName(username);
				UserFactory.updateInResponse(currentUser, response);
			}
		}
		
		// Update user profile image
		if (file != null && !file.isEmpty()) {
			// TODO: check if file is png first
            try {
            	String path = context.getRealPath("/resources/images")
            			+ File.separator
            			+ username + ".png";
            	
            	logger.info("Saving new profile image: " + path);
            	
                File destinationFile = new File(path);
				file.transferTo(destinationFile); // will delete any existing file first
			}
            catch (IllegalStateException | IOException ex) {
				logger.error(ex);
			}
		}
		
		response.setStatus(HttpServletResponse.SC_OK);
		String msg = "Successfully changed values!\\\\nusername: %1$s\\\\nReal Name: %2$s\\\\nBlab Name: %3$s";
		String respTemplate = "{\"values\": {\"username\": \"%1$s\", \"realName\": \"%2$s\", \"blabName\": \"%3$s\"}, \"message\": \"<script>alert('" + msg + "');</script>\"}";
		return String.format(respTemplate, username.toLowerCase(), realName, blabName);
	}
	
	private boolean usernameExists(String username) {
		username = username.toLowerCase();
		
		// Check is the username already exists
		Connection connect = null;
		PreparedStatement sqlStatement = null;
		try {
			logger.info("Getting Database connection");
			// Get the Database Connection
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());
			
			logger.info("Preparing the duplicate username check Prepared Statement");
			sqlStatement = connect.prepareStatement("SELECT username FROM users WHERE username=?");
			sqlStatement.setString(1, username);
			ResultSet result = sqlStatement.executeQuery();
			
			if (!result.first()) {
				// username does not exist
				return false;
			}
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		finally {
			try {
				if (sqlStatement != null) {
					sqlStatement.close();
				}
			}
			catch (SQLException e) {
				logger.error(e);
			}
			try {
				if (connect != null) {
					connect.close();
				}
			}
			catch (SQLException e) {
				logger.error(e);
			}
		}
		
		logger.info("Username: " + username + " already exists. Try again.");
		return true;
	}
	
	/**
	 * Change the user's username. Since the username is the DB key, we have a lot to do
	 * @param oldUsername Prior username
	 * @param newUsername Desired new username
	 * @return
	 */
	private boolean updateUsername(String oldUsername, String newUsername) {
		// Enforce all lowercase usernames
		oldUsername = oldUsername.toLowerCase();
		newUsername = newUsername.toLowerCase();
		
		// Check is the username already exists
		Connection connect = null;
		List<PreparedStatement> sqlUpdateQueries = new ArrayList<PreparedStatement>();
		try {
			logger.info("Getting Database connection");
			// Get the Database Connection
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection(Constants.create().getJdbcConnectionString());
			connect.setAutoCommit(false);
			
			// Update all references to this user
			 String[] sqlStrQueries= new String[] {
					"UPDATE users SET username=? WHERE username=?",
					"UPDATE blabs SET blabber=? WHERE blabber=?",
					"UPDATE comments SET blabber=? WHERE blabber=?",
					"UPDATE listeners SET blabber=? WHERE blabber=?",
					"UPDATE listeners SET listener=? WHERE listener=?",
					"UPDATE users_history SET blabber=? WHERE blabber=?"
			};
			for (String sql : sqlStrQueries) {
				logger.info("Preparing the Prepared Statement: " + sql);
				sqlUpdateQueries.add(connect.prepareStatement(sql));
			}
			
			// Execute updates as part of a batch transaction
			// This will roll back all changes if one query fails
			for (PreparedStatement stmt : sqlUpdateQueries) {
				stmt.setString(1, newUsername);
				stmt.setString(2, oldUsername);
				stmt.executeUpdate();
			}
			connect.commit();
			
			// Rename the user profile image to match new username
			logger.info("Renaming profile image from " + oldUsername + ".png to " + newUsername + ".png");
			String path = context.getRealPath("/resources/images")
        			+ File.separator + "%s.png";
			
			File oldName = new File(String.format(path, oldUsername));
			File newName = new File(String.format(path, newUsername));
			oldName.renameTo(newName);
			
			return true;
		}
		catch (SQLException | ClassNotFoundException ex) {
			logger.error(ex);
		}
		finally {
			try {
				if (sqlUpdateQueries != null) {
					for (PreparedStatement stmt : sqlUpdateQueries) {
						stmt.close();
					}
				}
			}
			catch (SQLException e) {
				logger.error(e);
			}
			try {
				if (connect != null) {
					logger.error("Transaction is being rolled back");
	                connect.rollback();
					connect.close();
				}
			}
			catch (SQLException e) {
				logger.error(e);
			}
		}
		
		// Error occurred
		return false;
	}
	
	public String displayErrorForWeb(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		String stackTrace = sw.toString();
		pw.flush();
		pw.close();
		return stackTrace.replace(System.getProperty("line.separator"), "<br/>\n");
	}
	
	public void emailExceptionsToAdmin(Throwable t) {
		String to = "admin@example.com";
		String from = "verademo@veracode.com";
		String host = "localhost";
		String port = "5555";

		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);

		Session session = Session.getDefaultInstance(properties);

		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			
			/* START BAD CODE */
			message.setSubject("Error detected: " + t.getMessage());
			/* END BAD CODE */
			
			message.setText(t.getMessage() + "<br>" + properties.getProperty("test") +  displayErrorForWeb(t));

			logger.info("Sending email to admin");
			Transport.send(message);
		}catch (MessagingException mex) {
			mex.printStackTrace();
		}
	}
}
