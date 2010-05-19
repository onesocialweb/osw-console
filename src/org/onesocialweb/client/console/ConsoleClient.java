/*
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *    
 */

/**
 * This class is largely inspired by the ConsoleClient of the FedOne Wave
 * server, copyrighted to Google Inc. The original source code for this class
 * can be found at htt://waveprotocol.org
 * Package: org.waveprotocol.wave.examples.fedone.waveclient;
 *
 */

package org.onesocialweb.client.console;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import jline.ANSIBuffer;
import jline.Completor;
import jline.ConsoleReader;

import org.onesocialweb.client.Inbox;
import org.onesocialweb.client.InboxEventHandler;
import org.onesocialweb.client.OswService;
import org.onesocialweb.client.OswServiceFactory;
import org.onesocialweb.client.exception.AuthenticationRequired;
import org.onesocialweb.client.exception.ConnectionException;
import org.onesocialweb.client.exception.ConnectionRequired;
import org.onesocialweb.client.exception.RequestException;
import org.onesocialweb.model.acl.AclAction;
import org.onesocialweb.model.acl.AclFactory;
import org.onesocialweb.model.acl.AclRule;
import org.onesocialweb.model.acl.AclSubject;
import org.onesocialweb.model.acl.DefaultAclFactory;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityFactory;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.activity.ActivityVerb;
import org.onesocialweb.model.activity.DefaultActivityFactory;
import org.onesocialweb.model.atom.AtomFactory;
import org.onesocialweb.model.atom.DefaultAtomFactory;
import org.onesocialweb.model.relation.DefaultRelationFactory;
import org.onesocialweb.model.relation.Relation;
import org.onesocialweb.model.relation.RelationFactory;
import org.onesocialweb.model.vcard4.BirthdayField;
import org.onesocialweb.model.vcard4.DefaultVCard4Factory;
import org.onesocialweb.model.vcard4.EmailField;
import org.onesocialweb.model.vcard4.Field;
import org.onesocialweb.model.vcard4.FullNameField;
import org.onesocialweb.model.vcard4.GenderField;
import org.onesocialweb.model.vcard4.NoteField;
import org.onesocialweb.model.vcard4.PhotoField;
import org.onesocialweb.model.vcard4.Profile;
import org.onesocialweb.model.vcard4.TelField;
import org.onesocialweb.model.vcard4.TimeZoneField;
import org.onesocialweb.model.vcard4.URLField;
import org.onesocialweb.model.vcard4.VCard4Factory;
import org.onesocialweb.model.vcard4.exception.CardinalityException;
import org.onesocialweb.model.vcard4.exception.UnsupportedFieldException;
import org.onesocialweb.smack.OswServiceFactoryImp;

import com.google.common.collect.ImmutableList;

public class ConsoleClient implements InboxEventHandler {

	/** Default prompt */
	private static final String DEFAULT_PROMPT = "not connected";

	/** Default XMPP port */
	private static final Integer XMPP_DEFAULT_PORT = 5222;

	/** Single active console reader. */
	private final ConsoleReader reader;

	/** Default acl setting for activities */
	private List<AclRule> defaultRules;

	/** One social web API */
	private OswService service;

	/** The profile of the logged in user */
	private Profile profile;

	/** The inbox of the logged in user */
	private Inbox inbox;

	/** The current user JID */
	private String bareJid;

	/**
	 * Dependencies (should be injected in some way)
	 */

	private ActivityFactory activityFactory = new DefaultActivityFactory();
	
	private RelationFactory relationFactory = new DefaultRelationFactory();

	private AtomFactory atomFactory = new DefaultAtomFactory();

	private AclFactory aclFactory = new DefaultAclFactory();

	private VCard4Factory profileFactory = new DefaultVCard4Factory();

	private OswServiceFactory oswServiceFactory = new OswServiceFactoryImp();

	/**
	 * PrintStream to use for output. We don't use ConsoleReader's functionality
	 * because it's too verbose and doesn't really give us anything in return.
	 */
	private final PrintStream out = System.out;

	private class Command {
		public final String name;
		public final String args;
		public final String description;

		private Command(String name, String args, String description) {
			this.name = name;
			this.args = args;
			this.description = description;
		}
	}

	/** Commands available to the user. */
	private final List<Command> commands = ImmutableList.of(
			new Command("connect", "server [port]", "connect to server at optional port"),
			new Command("disconnect", "", "diconnect from server"),
			new Command("login", "username", "login user username, password will be prompted"), 
			new Command("register", "", "register a new user on the connected host"),
			new Command("inbox", "", "shows the current user inbox"),
			new Command("activities", "[jid]", "shows the activities of the current user or another jid"),
			new Command("subscribe", "jid", "subscribe to the givn jid activity stream"),
			new Command("subscriptions", "[jid]", "list of users the current user is subscribed to (following)"), 
			new Command("subscribers", "[jid]", "list of users subscribed to current user (followers)"),
			new Command("unsubscribe", "jid", "unsubscribe from the given jid activity stream"), 
			new Command("relations", "[jid]", "shows the relations of the current user or another jid"),
 			new Command("profile", "[jid]", "view the profile of the current user or another jid"), 
 			new Command("privacy", "", "change the current privacy default (used when posting activities)"),
			new Command("set", "key value [type]", "add a given key value in the profile"),
			new Command("clear", "key", "remove all entries with given key from the profile"),
			new Command("relation", "[add|update] [id]", "add or update a relation"),
			new Command("upload", "", "display an upload token"),
			new Command("delete", "activityNr", "delete the activity selected if posted by this user"),
			new Command("update", "activityNr", "edits the activity selected if posted by this user"),
			new Command("comment", "activityNr", "comments on the activity selected"),
			new Command("replies", "activityNr", "lists the replies of the activity selected"),
			new Command("quit", "", "quit the client"));

	/**
	 * Create new console client.
	 */
	public ConsoleClient() throws IOException {
		reader = new ConsoleReader();
		service = oswServiceFactory.createService();

		// And tab completion
		reader.addCompletor(new Completor() {
			@SuppressWarnings("unchecked")
			@Override
			public int complete(String buffer, int cursor, List candidates) {
				if (buffer.trim().startsWith("/")) {
					buffer = buffer.trim().substring(1);
				}

				for (Command cmd : commands) {
					if (cmd.name.startsWith(buffer)) {
						candidates.add('/' + cmd.name + ' ');
					}
				}

				return 0;
			}
		});

		// Build the default ACL
		AclRule rule = aclFactory.aclRule();
		rule.addSubject(aclFactory.aclSubject(null, AclSubject.EVERYONE));
		rule.addAction(aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT));
		defaultRules = new ArrayList<AclRule>();
		defaultRules.add(rule);
	}

	/**
	 * Entry point for the user interface, receives user input, terminates on
	 * EOF.
	 * 
	 * @param args
	 *            command line arguments
	 */
	public void run(String[] args) throws IOException {
		// Initialise screen and move cursor to bottom left corner
		
		reader.clearScreen();
		setPrompt(DEFAULT_PROMPT);		
			
		if (args.length!=0)		{
			processArgs(args);
		}
				
		out.println(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.startsWith("/")) {
				doCommand(extractCmd(line), extractArgs(line));
			} else if (line.length() > 0) {
				try {
					updateStatus(line);
				} catch (ConnectionRequired e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (AuthenticationRequired e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}		
		
		// And yet there still seem to be threads hanging around
		System.exit(0);
	}

	/**
	 * Validate and perform connect and/or login if args were given when the 
	 * application was launched.
	 * 	
	 * @param args
	 *            list of command-line arguments 
	 */
	private void processArgs(String args[])
	{
		boolean hasPort=false;	
		if (Arrays.asList(args).contains(new String("-p"))) 
				hasPort=true;
					
		if (hasPort) 
		{
			if (args.length<=2)
				badArgs("connect");
			else {
				try {
					Integer.parseInt(args[2]);
				}catch(NumberFormatException e){
					badArgs("connect");
				}	
			}
			doCommand(extractCmd("/connect "+args[0]), extractArgs("/connect "+args[0]  + " " + args[2] ));
		}		
		else
			doCommand(extractCmd("/connect "+args[0]), extractArgs("/connect "+args[0]));			
			
		switch(args.length)
		{
			case 2:
				doCommand(extractCmd("/login "+args[1]) , extractArgs("/login "+args[1])); break;
			case 3:
				if (!hasPort)
					doCommand(extractCmd("/login "+args[1] + " " +args[2] ), extractArgs("/login "+args[1]+ " " +args[2] )); break;
			case 4: 
				doCommand(extractCmd("/login "+args[3]), extractArgs("/login "+args[3] )); break;
			case 5: 
				doCommand(extractCmd("/login "+args[3] + " " +args[4] ), extractArgs("/login "+args[3]+ " " +args[4] )); break;
		}
	}
	

	
	/**
	 * Perform command with given arguments.
	 * 
	 * @param cmd
	 *            command string to perform
	 * @param args
	 *            list of arguments to the command
	 */
	private void doCommand(String cmd, List<String> args) {
		try {
			if (cmd.equals("connect")) {
				if (args.size() == 1) {
					connect(args.get(0), XMPP_DEFAULT_PORT);
				} else if (args.size() == 2) {
					try {
						Integer port = Integer.parseInt(args.get(1));
						connect(args.get(0), port);
					} catch (NumberFormatException e) {
						badArgs(cmd);
					}
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("login")) {
				if (args.size() == 1) {
					login(args.get(0));
				}
				else if (args.size() == 2)
				{
					login(args.get(0), args.get(1));
				}
				else {
					badArgs(cmd);
				}
			} else if (cmd.equals("upload")) {
				if (args.size() == 0) {
					upload();
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("disconnect")) {
				if (args.size() == 0) {
					disconnect();
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("register")) {
				if (args.size() == 0) {
					register();
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("activities")) {
				if (args.size() == 0) {
					activities(null);
				} else if (args.size() == 1) {
					activities(args.get(0));
				} else {
					badArgs(cmd);
				}
			}  else if (cmd.equals("shout")) {
				if (args.size() == 1) {
					shout(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("relations")) {
				if (args.size() == 0) {
					relations(bareJid);
				} else if (args.size() == 1) {
					relations(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("subscribe")) {
				if (args.size() == 1) {
					subscribe(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("subscriptions")) {
				if (args.size() == 0) {
					subscriptions(bareJid);
				} else if (args.size() == 1) {
					subscriptions(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("subscribers")) {
				if (args.size() == 0) {
					subscribers(bareJid);
				} else if (args.size() == 1) {
					subscribers(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("profile")) {
				if (args.size() == 1) {
					profile(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("set")) {
				if (args.size() == 1) {
					setProfileKey(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("clear")) {
				if (args.size() == 1) {
					clear(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("unsubscribe")) {
				if (args.size() == 1) {
					unsubscribe(args.get(0));
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("inbox")) {
				if (args.size() == 0) {
					inbox();
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("privacy")) {
				if (args.size() == 0) {
					privacy();
				} else {
					badArgs(cmd);
				}
			}else if (cmd.equals("delete")) {
				if (args.size() == 1) {
					delete(args.get(0));
				} else {
					badArgs(cmd);
				}
			}else if (cmd.equals("update")) {
				if (args.size() == 1) {
					updateActivity(args.get(0));
				} else {
					badArgs(cmd);
				}
			}else if (cmd.equals("comment")) {
				if (args.size() == 1) {
					commentActivity(args.get(0));
				} else {
					badArgs(cmd);
				}				
			} else if (cmd.equals("replies")) {
				if (args.size() == 1) {
					queryReplies(args.get(0));
				} else {
					badArgs(cmd);
				}
			}
			else if (cmd.equals("relation")) {
				if (args.size() > 0) {
					if (args.get(0).equals("add")) {
						if (args.size() == 1) {
							addRelation();
						} else {
							error("too many arguments, expecting: /relation add");
						}
					} else if (args.get(0).equals("update")) {
						if (args.size() == 2) {
							updateRelation(args.get(1));
						} else {
							error("incorrect arguments, expecting: /relation update [relation-id]");
						}
						
					} else {
						badArgs(cmd);
					}
				} else {
					badArgs(cmd);
				}
			} else if (cmd.equals("quit")) {
				System.exit(0);
			} else {
				printHelp();
			}
		} catch (AuthenticationRequired e) {
			error("You must first be logged in to perform this command");
		} catch (ConnectionRequired e) {
			error("You must first be connected to perform this command");
		} catch (IOException e) {
			error("Ooops !" + e.getMessage());
		}
	}

	private void connect(String server, Integer port) {
		try {
			service.setCompressionEnabled(false);
			service.setReconnectionAllowed(true);
			service.connect(server, port, null);
		} catch (ConnectionException e) {
			e.printStackTrace();
			return;
		}

		// Update the prompt
		reader.setDefaultPrompt("(" + service.getHostname() + ") ");
	}

	private void disconnect() throws ConnectionRequired {
		service.disconnect();
		reader.setDefaultPrompt(DEFAULT_PROMPT);
		out.println("You have been successfully disconected");
	}

	private void register() throws ConnectionRequired {

		String email, username, name, password;

		// Ask the user for data
		String prompt = reader.getDefaultPrompt();
		try {
			username = reader.readLine("Username: ");
			name = reader.readLine("Name: ");
			email = reader.readLine("Email: ");
			password = reader.readLine("Password: ", new Character('*'));
		} catch (IOException e) {
			return;
		}
		reader.setDefaultPrompt(prompt);

		// Prepare the request
		service.register(username, password, name, email);
	}
	
	private void login (String username, String password) throws ConnectionRequired
	{
		
		try {
			service.login(username, password, "console");
		} catch (RequestException e1) {
			e1.printStackTrace();
			return;
		}
		inbox = service.getInbox();
		inbox.refresh();
		inbox.registerInboxEventHandler(this);
		render();

		// Fetch the user profile
		try {
			profile = service.getProfile(null);
		} catch (RequestException e) {
		} catch (AuthenticationRequired e) {
		}

		// Set the user
		this.bareJid = username + "@" + service.getHostname();

		// Restore the prompt
		reader.setDefaultPrompt("(" + service.getUser() + ") ");
	}

	private void login(String username) throws ConnectionRequired {

		String password;

		// First get the password
		String prompt = reader.getDefaultPrompt();
		try {
			password = reader.readLine("Password: ", new Character('*'));
		} catch (IOException e) {
			password = null;
		}
		reader.setDefaultPrompt(prompt);
		login (username, password);
		
	}
	
	private void upload() throws ConnectionRequired, AuthenticationRequired, IOException {
		try {
			reader.printString("Session ID: " + service.getUploadToken("0"));	
		} catch (RequestException e) {
			e.printStackTrace();
		}
	}

	private void activities(String jid) throws ConnectionRequired, AuthenticationRequired {
		try {
			renderActivities(service.getActivities(jid));
		} catch (RequestException e) {
			e.printStackTrace();
		}
	}
	
	private void relations(String jid) throws ConnectionRequired, AuthenticationRequired {
		try {
			renderRelations(service.getRelations(jid));
		} catch (RequestException e) {
			e.printStackTrace();
		}
	}

	private void inbox() throws ConnectionRequired, AuthenticationRequired {
		inbox.refresh();
		render();
	}

	private void subscribe(String user) throws ConnectionRequired, AuthenticationRequired {
		try {
			service.subscribe(user);
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void unsubscribe(String user) throws ConnectionRequired, AuthenticationRequired {
		try {
			service.unsubscribe(user);
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void profile(String user) throws ConnectionRequired, AuthenticationRequired {
		try {
			Profile userProfile = service.getProfile(user);
			if (userProfile != null) {
				render(userProfile);
			}
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void subscriptions(String jid) throws ConnectionRequired, AuthenticationRequired {
		try {
			List<String> subscriptions = service.getSubscriptions(jid);
			if (subscriptions != null && subscriptions.size() > 0) {
				render("Subscriptions of " + jid, subscriptions);
			}
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void subscribers(String jid) throws ConnectionRequired, AuthenticationRequired {
		try {
			List<String> subscribers = service.getSubscribers(jid);
			if (subscribers != null && subscribers.size() > 0) { 
				render("Subscribers to " + jid, subscribers);
			}
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void addRelation() throws IOException, AuthenticationRequired, ConnectionRequired {
		final String prompt = reader.getDefaultPrompt();
		String user = reader.readLine("User :");
		String nature = reader.readLine("Nature :");
		String message = reader.readLine("Message :");

		try {
			Relation relation = relationFactory.relation();
			relation.setNature(nature);
			relation.setStatus(Relation.Status.REQUEST);
			relation.setFrom(bareJid);
			relation.setTo(user);
			relation.setMessage(message);
			service.addRelation(relation);
		} catch (RequestException e) {
			e.printStackTrace();
		}

		reader.printString("Relation request sent.");
		reader.setDefaultPrompt(prompt);
	}
	
	private void updateRelation(String id) throws IOException, AuthenticationRequired, ConnectionRequired {
		final String prompt = reader.getDefaultPrompt();
		String status = reader.readLine("Status :");
		
		try {
			Relation relation = relationFactory.relation();
			relation.setId(id);
			relation.setStatus(status);
			service.updateRelation(relation);
		} catch (RequestException e) {
			e.printStackTrace();
		}

		reader.printString("Relation update sent.");
		reader.setDefaultPrompt(prompt);
	}


	private void setProfileKey(String key) throws ConnectionRequired, AuthenticationRequired, IOException {
		if (profile == null) {
			profile = profileFactory.profile();
			profile.setUserId(bareJid);
		}
		
		final String prompt = reader.getDefaultPrompt();
		
		if (key.equals(PhotoField.NAME)) {
			String value = reader.readLine("Photo uri :");
			Field field = profileFactory.photo(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(PhotoField.NAME)) {
				profile.removeAll(PhotoField.NAME);
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		} else if (key.equals(BirthdayField.NAME)) {
			String value = reader.readLine("Birthday :");
			Field field = profileFactory.birthday();
			try {
				Date date=new SimpleDateFormat("dd/MM/yyyy").parse(value);
				 field = profileFactory.birthday(date);
			}catch (ParseException e)
			{			
			}
			
			field.setAclRules(defaultRules);
			
			if (profile.hasField(BirthdayField.NAME)) {
				profile.removeField(profile.getField(BirthdayField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}  
		else if (key.equals(GenderField.NAME)) {
			String value = reader.readLine("Gender :");
			Field field = profileFactory.gender();
			try {
				 int gender=Integer.parseInt(value);
				 switch (gender) {
				case 0:
					field = profileFactory.gender(GenderField.Type.NOTKNOWN);;
					break;
				case 1:
					field = profileFactory.gender(GenderField.Type.MALE);;
					break;
				case 2:
					field = profileFactory.gender(GenderField.Type.FEMALE);;
					break;
				case 3:
					field = profileFactory.gender(GenderField.Type.NOTAPPLICABLE);;
					break;
				}
				 
			}catch (Exception e)
			{			
				e.printStackTrace();
			}
			
			field.setAclRules(defaultRules);
			
			if (profile.hasField(GenderField.NAME)) {
				profile.removeField(profile.getField(GenderField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}	
		
		else if (key.equals(FullNameField.NAME)) {
			String value = reader.readLine("Display name :");
			Field field = profileFactory.fullname(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(FullNameField.NAME)) {
				profile.removeField(profile.getField(FullNameField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		} 
		else if (key.equals(NoteField.NAME)) {
			String value = reader.readLine("Bio :");
			Field field = profileFactory.note(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(NoteField.NAME)) {
				profile.removeField(profile.getField(NoteField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}  else if (key.equals(URLField.NAME)) {
			String value = reader.readLine("Url :");
			Field field = profileFactory.url(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(URLField.NAME)) {
				profile.removeField(profile.getField(URLField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}  else if (key.equals(TimeZoneField.NAME)) {
			String value = reader.readLine("TimeZone :");
			Field field = profileFactory.timeZone(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(TimeZoneField.NAME)) {
				profile.removeField(profile.getField(TimeZoneField.NAME));
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		} else if (key.equals(EmailField.NAME)) {
			String value = reader.readLine("Email :");
			Field field = profileFactory.email(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(EmailField.NAME)) {
				profile.removeAll(EmailField.NAME);
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}  else if (key.equals(TelField.NAME)) {
			String value = reader.readLine("Tel :");
			Field field = profileFactory.tel(value);
			field.setAclRules(defaultRules);
			
			if (profile.hasField(TelField.NAME)) {
				profile.removeAll(TelField.NAME);
			}
			
			try {
				profile.addField(field);
			} catch (UnsupportedFieldException e) {
				e.printStackTrace();
			} catch (CardinalityException e) {
				e.printStackTrace();
			}
		}  			
		
		try {
			service.setProfile(profile);
		} catch (RequestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		reader.printString("Profile updated.");
		reader.setDefaultPrompt(prompt);
	}

	private void clear(String key) throws ConnectionRequired, AuthenticationRequired {
		if (profile != null) {
			for (Iterator<Field> i = profile.getFields().iterator(); i.hasNext();) {
				Field field = i.next();
				if (field.getName().equals(key)) {
					i.remove();
				}
			}
			try {
				service.setProfile(profile);
			} catch (RequestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void shout(String recipient) throws ConnectionRequired, AuthenticationRequired, IOException {

		final String prompt = reader.getDefaultPrompt();
		String message = reader.readLine("Message :");
		
		ActivityObject object = activityFactory.object();
		object.setType(ActivityObject.STATUS_UPDATE);
		object.addContent(atomFactory.content(message, "text/plain", null));

		ActivityEntry entry = activityFactory.entry();
		entry.setPublished(Calendar.getInstance().getTime());
		entry.addVerb(activityFactory.verb(ActivityVerb.POST));
		entry.addObject(object);
		entry.setAclRules(defaultRules);
		entry.setTitle(message);
		entry.addRecipient(atomFactory.reply(null, recipient, null, null));

		try {
			service.postActivity(entry);
		} catch (RequestException e) {
			e.printStackTrace();
		}
		
		reader.setDefaultPrompt(prompt);

	}
	
		private void updateStatus(String message) throws ConnectionRequired, AuthenticationRequired {
			if (message == null || message.isEmpty()) {
				return;
			}

			ActivityObject object = activityFactory.object();
			object.setType(ActivityObject.STATUS_UPDATE);
			object.addContent(atomFactory.content(message, "text/plain", null));

			ActivityEntry entry = activityFactory.entry();
			entry.setPublished(Calendar.getInstance().getTime());
			entry.addVerb(activityFactory.verb(ActivityVerb.POST));
			entry.addObject(object);
			entry.setAclRules(defaultRules);
			entry.setTitle(message);

			try {
				service.postActivity(entry);
			} catch (RequestException e) {
				e.printStackTrace();
			}
	}

	public void privacy() throws IOException {
		final List<AclRule> aclRules = new ArrayList<AclRule>();
		final AclRule aclRule = aclFactory.aclRule();
		final String prompt = reader.getDefaultPrompt();
		AclSubject subject;
		AclAction action;

		String mode = reader.readLine("Privacy mode [E/G/I/N] ?");

		if (mode.equalsIgnoreCase("e")) {
			action = aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT);
			subject = aclFactory.aclSubject(null, AclSubject.EVERYONE);
		} else if (mode.equalsIgnoreCase("g")) {
			String group = reader.readLine("Group name: ");
			action = aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT);
			subject = aclFactory.aclSubject(group, AclSubject.GROUP);
		} else if (mode.equalsIgnoreCase("i")) {
			String user = reader.readLine("User id: ");
			action = aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT);
			subject = aclFactory.aclSubject(user, AclSubject.PERSON);
		} else if (mode.equalsIgnoreCase("n")) {
			action = aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_DENY);
			subject = aclFactory.aclSubject(null, AclSubject.EVERYONE);
		} else {
			return;
		}

		aclRule.addAction(action);
		aclRule.addSubject(subject);

		aclRules.add(aclRule);

		defaultRules = aclRules;

		reader.printString("Your privacy has changed.");
		reader.setDefaultPrompt(prompt);

	}

	/**
	 * Print help.
	 */
	private void printHelp() {
		int maxNameLength = 0;
		int maxArgsLength = 0;

		for (Command cmd : commands) {
			maxNameLength = Math.max(maxNameLength, cmd.name.length());
			maxArgsLength = Math.max(maxArgsLength, cmd.args.length());
		}

		out.println("Commands:");
		for (Command cmd : commands) {
			out.printf(String.format("  %%-%ds  %%-%ds  %%s\n", maxNameLength, maxArgsLength), cmd.name, cmd.args, cmd.description);
		}

		out.println();
	}

	/**
	 * Print some error message when there are bad arguments to a user interface
	 * command.
	 * 
	 * @param cmd
	 *            the bad command
	 */
	private void badArgs(String cmd) {
		error("incorrect number of arguments to " + cmd + ", expecting: /" + cmd + " " + findCommand(cmd).args);
	}

	private void message(String message) {
		StringBuilder buf = new StringBuilder();
		// Save the current position
		buf.append(ANSIBuffer.ANSICodes.save());
		// Clear the current line
		buf.append(ANSIBuffer.ANSICodes.clreol());
		// Output the error message
		buf.append(message + "\r\n");
		// Display what the user was typing when message was received
		buf.append(reader.getDefaultPrompt());
		buf.append(reader.getCursorBuffer());
		// Restore cursor
		buf.append(ANSIBuffer.ANSICodes.restore());

		out.print(buf);
		out.flush();
	}

	private void error(String message) {
		message("Error: " + message);
	}

	/**
	 * @param command
	 *            name
	 * @return the {@code Command} object from commands for command, or null if
	 *         not found
	 */
	private Command findCommand(String command) {
		for (Command cmd : commands) {
			if (command.equals(cmd.name)) {
				return cmd;
			}
		}

		return null;
	}

	private void setPrompt(String prompt) {
		reader.setDefaultPrompt("(" + prompt + ") :");
	}

	private void render() {
		renderActivities(inbox.getEntries());
	}

	private void renderActivities(List<ActivityEntry> activities) {
		StringBuilder buf = new StringBuilder();

		// Clear screen
		buf.append(ANSIBuffer.ANSICodes.save());
		buf.append(ANSIBuffer.ANSICodes.gotoxy(1, 1));
		buf.append(((char) 27) + "[J");

		int i=1;
		// Paint the activities
		if (activities != null && !activities.isEmpty()) {
			for (ActivityEntry activity : activities) {
				buf.append("(" + i++ + ") ");
				if (activity.hasReplies())
					buf.append("(Replies : " + activity.getRepliesLink().getCount() + ") ");
				buf.append(render(activity));
			}
		}

		// Draw what the user was typing at the time of rendering
		buf.append(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));
		buf.append(reader.getDefaultPrompt());
		buf.append(reader.getCursorBuffer());

		// Restore cursor
		buf.append(ANSIBuffer.ANSICodes.restore());

		out.print(buf);
		out.flush();
	}
	
	private void renderRelations(List<Relation> relations) {
		StringBuilder buf = new StringBuilder();

		// Clear screen
		buf.append(ANSIBuffer.ANSICodes.save());
		buf.append(ANSIBuffer.ANSICodes.gotoxy(1, 1));
		buf.append(((char) 27) + "[J");

		// Paint the activities
		if (relations != null && !relations.isEmpty()) {
			for (Relation relation : relations) {
				buf.append(render(relation));
			}
		}

		// Draw what the user was typing at the time of rendering
		buf.append(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));
		buf.append(reader.getDefaultPrompt());
		buf.append(reader.getCursorBuffer());

		// Restore cursor
		buf.append(ANSIBuffer.ANSICodes.restore());

		out.print(buf);
		out.flush();
	}

	private String render(ActivityEntry activity) {

		String author = (activity.hasActor()) ? activity.getActor().getUri() : null;
		String status = (activity.hasTitle()) ? activity.getTitle() : null;
		String published = (activity.hasPublished()) ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
				activity.getPublished()) : null;

		String result = published + " \t| " + (author != null ? author + " \t| " : "") + status + "\n";
		return result;
	}

	private String render(Relation relation) {

		String published = (relation.hasPublished()) ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
				relation.getPublished()) : null;
		String origin = relation.hasFrom() ? relation.getFrom() : null;
		String target = relation.hasTo() ? relation.getTo() : null;
		String nature = relation.hasNature() ? relation.getNature() : null;
		String status = relation.hasStatus() ? relation.getStatus() : null;
		String message = relation.hasMessage() ? relation.getMessage() : null;
		String id = relation.hasId() ? relation.getId() : null;

		String result = (published != null ? published + " \t| " : "") + (origin != null ? origin + " \t| " : "") + (target != null ? target + " \t| " : "") + "\n"
					+ (nature != null ? "Nature: " + nature + "\n" : "") 
					+ (status != null ? "Status: " + status + "\n" : "")
					+ (message != null ? "Message: " + message + "\n" : "")
				    + (id != null ? "Id: " + id + "\n" : "");

		return result;
	}

	private void render(Profile profile) {
		StringBuilder buf = new StringBuilder();

		// Clear screen
		buf.append(ANSIBuffer.ANSICodes.save());
		buf.append(ANSIBuffer.ANSICodes.gotoxy(1, 1));
		buf.append(((char) 27) + "[J");

		// Print user name
		buf.append("Profile of " + profile.getUserId() + "\n");

		// Paint the profile fields
		for (Field field : profile.getFields()) {
			buf.append(field.getName() + ": " + field.getValue() + "\n");
		}

		// Draw what the user was typing at the time of rendering
		buf.append(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));
		buf.append(reader.getDefaultPrompt());
		buf.append(reader.getCursorBuffer());

		// Restore cursor
		buf.append(ANSIBuffer.ANSICodes.restore());

		out.print(buf);
		out.flush();
	}
	
	private void render(String header, List<String> lines) {
		StringBuilder buf = new StringBuilder();

		// Clear screen
		buf.append(ANSIBuffer.ANSICodes.save());
		buf.append(ANSIBuffer.ANSICodes.gotoxy(1, 1));
		buf.append(((char) 27) + "[J");

		// Print the header
		buf.append(header + "\n");

		// Paint the lines
		for (String line : lines) {
			buf.append(line + "\n");
		}

		// Draw what the user was typing at the time of rendering
		buf.append(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));
		buf.append(reader.getDefaultPrompt());
		buf.append(reader.getCursorBuffer());

		// Restore cursor
		buf.append(ANSIBuffer.ANSICodes.restore());

		out.print(buf);
		out.flush();
	}
	
	private void delete(String actNr) throws ConnectionRequired, AuthenticationRequired
	{		
		int intActNr=Integer.parseInt(actNr);
		List<ActivityEntry> activities=inbox.getEntries();		
		
		if (activities==null)
			return;
		
		if ((intActNr<0) || (intActNr>activities.size()))
				return;
		
		ActivityEntry activity=null;
		if (activities.size()>0)
			activity = activities.get(intActNr-1);
		
		try {
			if (activity!=null){
				service.deleteActivity(activity.getId());
				inbox.refresh();
			}
		} catch (RequestException e) {
			e.printStackTrace();
		}				
	}
	
	private void updateActivity(String actNr) throws ConnectionRequired, AuthenticationRequired
	{		
		String newStatus=new String();
		// First get the new status message for the activity
		String prompt = reader.getDefaultPrompt();
		try {
			newStatus = reader.readLine("New message for the activity: ");
		} catch (IOException e) {
			newStatus = "";
		}
		int intActNr=Integer.parseInt(actNr);
		List<ActivityEntry> activities=inbox.getEntries();		
		
		if (activities==null)
			return;
		
		if ((intActNr<0) || (intActNr>activities.size()))
				return;
		
		ActivityEntry activity=null;
		if (activities.size()>0)
			activity = activities.get(intActNr-1);
		
		try {
			if (activity!=null){
				activity.setTitle(newStatus);
				service.updateActivity(activity);
				inbox.refresh();
			}
		} catch (RequestException e) {
			e.printStackTrace();
		} 				
		reader.setDefaultPrompt(prompt);
	}
	
	private void commentActivity(String actNr) throws ConnectionRequired, AuthenticationRequired
	{	
		String comment=new String();
		// First get the comment message for the activity
		String prompt = reader.getDefaultPrompt();
		try {
			comment = reader.readLine("Enter your comment: ");
		} catch (IOException e) {
			comment = "";
		}		
		if (comment.length()==0)
			return;
		
		int intActNr=Integer.parseInt(actNr);
		List<ActivityEntry> activities=inbox.getEntries();		
		
		if (activities==null)
			return;		
		if ((intActNr<=0) || (intActNr>activities.size()))
				return;
		
		ActivityEntry activity=null;
		if (activities.size()>0)
			activity = activities.get(intActNr-1);
				
		
		ActivityObject object = activityFactory.object();
		object.setType(ActivityObject.COMMENT);
		object.addContent(atomFactory.content(comment, "text/plain", null));

		ActivityEntry commentEntry = activityFactory.entry();
		commentEntry.setPublished(Calendar.getInstance().getTime());
		commentEntry.addVerb(activityFactory.verb(ActivityVerb.POST));
		commentEntry.addObject(object);
		commentEntry.setAclRules(defaultRules);
		commentEntry.setTitle(comment);
		commentEntry.setParentId(activity.getId());
		commentEntry.setParentJID(activity.getActor().getUri());
		
		try {
			service.postActivity(commentEntry);
		} catch (RequestException e) {
			e.printStackTrace();
		}
		
		reader.setDefaultPrompt(prompt);
	}
	
	private void queryReplies(String actNr) throws ConnectionRequired, AuthenticationRequired
	{	
		int intActNr=Integer.parseInt(actNr);
		List<ActivityEntry> activities=inbox.getEntries();		
		
		if (activities==null)
			return;		
		if ((intActNr<=0) || (intActNr>activities.size()))
				return;
		
		ActivityEntry activity=null;
		if (activities.size()>0)
			activity = activities.get(intActNr-1);
		
		try {
			List<ActivityEntry> replies =service.getReplies(activity);
			renderActivities(replies);
		} catch (RequestException e) {
			e.printStackTrace();
		}
	}
	
	

	/**
	 * Extract the command from a command line String.
	 * 
	 * For example, extractCmd("/connect hello") returns "connect".
	 * 
	 * @param commandLine
	 *            the command line input
	 * @return command
	 */
	private String extractCmd(String commandLine) {
		return extractCmdBits(commandLine).get(0).substring(1);
	}

	/**
	 * Extract the command arguments from a command line String.
	 * 
	 * For example, extractArgs("/connect hello") returns ["hello"].
	 * 
	 * @param commandLine
	 *            the command line input
	 * @return list of command arguments
	 */
	private List<String> extractArgs(String commandLine) {
		List<String> bits = extractCmdBits(commandLine);
		return bits.subList(1, bits.size());
	}

	/**
	 * Extract a list of command line components from a command line String.
	 * 
	 * For example, extractCmdBits("/connect hello") return ["/connect",
	 * "hello"].
	 * 
	 * @param commandLine
	 *            the command line input
	 * @return list of command line components
	 */
	private List<String> extractCmdBits(String commandLine) {
		return Arrays.asList(commandLine.trim().split(" +"));
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			ConsoleClient ui = new ConsoleClient();
			ui.run(args);
		} catch (IOException e) {
			System.err.println("IOException when running client: " + e);
		}
	}

	@Override
	public void onMessageDeleted(ActivityEntry entry) {
		render();	
	}

	@Override
	public void onMessageReceived(ActivityEntry entry) {
		render();	
	}

	@Override
	public void onRefresh(List<ActivityEntry> activities) {
		render();	
	}

	@Override
	public void onMessageUpdated(ActivityEntry entry) {
		render();
	}
}
