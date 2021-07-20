package client_game;

/**
 * Multiplayer Game Client Version
 * 
 * FILL OUT LATER
 * -1   ERROR
 *  0   WAIT
 *  1   GOOD
 * java -cp build -Djavax.net.ssl.trustStore=gamestore -Djavax.net.ssl.trustStorePassword=password client_game.GameClientMain localhost 4444
 */

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author noah.boushee
 * 
 * A client for a game, this client should be able to be used for any two player turn based game such as chess, checkers, tic tac toe, etc
 * Any game where a player makes one move than the other player makes one move till game is completed or player forfeits
 */
class GameClient {

	private int ID;
	private int playerNum;
	private String refreshCommand = "\tRefresh Available Lobbys: REFRESH\n";
	private String joinCommand = "\tJoin Lobby: JOIN <LOBBY NUMBER>\n";
	private String scoreBoardCommand = "\tSee Score Board: SCOREBOARD\n";
	private String exitCommand = "\tExit Server: EXIT\n";
	private String gameCommand = "MOVE PIECE: <INITIAL POSITION (Letter Number)> <DESTINATION (Letter Number)>\n FORFEIT OR EXIT: EXIT";
	private SSLSocket socket;
	private PrintWriter out;
	private BufferedReader in;
	private BufferedReader stdIn;
	

	GameClient(String host, int port){
		try{
			SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
			socket = (SSLSocket)factory.createSocket(host, port);
			System.out.println("CONNECTED TO SOCKET");
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			stdIn = new BufferedReader(new InputStreamReader(System.in));
			
		}
		catch(Exception e){
			System.out.println("ERROR " + e);
		}
	}

	/**
	* Reads input from user, moves to different methods/sections depending on input see @see
	*
	* 
	* @see joinRoutine
	* @see refreshRoutine
	* @see exitRoutine
	*/
	public void setUpGame(){
		try{
			//Server Client Authetication
			String inMsg = in.readLine();
			System.out.println("\tRECEIVED FROM SERVER: " + inMsg);
			if (! (inMsg.equals("CONNECTED"))){
				System.err.println("Connected to invalid server closing now!");
				System.exit(1);
			}
			System.out.println("CONNECTED TO SERVER");
			inMsg = in.readLine();
			ID = Integer.parseInt(inMsg);
			//GETS THE SERVERS
			System.out.println("\tCLIENT ID IS: " + inMsg);
			refreshRoutine("REFRESH");
			//printCommands();
			//System.out.print("\tRECEIVED FROM SERVER: " + in.readLine());
			String outMsg = stdIn.readLine();
			while (true){
				if (outMsg.toUpperCase().contains("JOIN")){
					//GO TO JOIN ROUTINE
					joinRoutine(outMsg);
				}
				else if(outMsg.toUpperCase().equals("REFRESH")){
					//GO TO REFRESH ROUTINE
					refreshRoutine(outMsg);
				}
				else if(outMsg.toUpperCase().equals("EXIT")){
					//GO TO EXIT ROUTINE
					exitRoutine(outMsg);
					break;
				}
				else if(outMsg.toUpperCase().equals("SCOREBOARD")){
					//GO TO EXIT ROUTINE
					scoreBoardRoutine(outMsg);
				}
				else{
					out.println(outMsg);
					inMsg = in.readLine();
					System.out.println("\tRECEIVED FROM SERVER: " + inMsg);
					
				}
				//ASK USER AGAIN WHAT THEY WANT TO DO
				refreshRoutine("REFRESH");
				outMsg = stdIn.readLine();
			}
		}
		catch(Exception e){
			System.out.println("ERROR " + e);
		}
	}
	
	
	/**
	* Handles the join case where a user is already in a game, the first user must wait for the other user to join before they can
	* do anything. Logic should probably be added for if the user doesn't want to just wait in the lobby. The verification of
	* moves are handled by the server
	*
	* @param msg message that is passed in, should always be join
	*/
	private void joinRoutine(String msg){
		boolean exit = false;
		boolean seenMSG = false;
		out.println(msg);
		System.out.println("SENDING: " + msg);
		try{
			String inMsg = in.readLine();
			System.out.println("RECEIVED: " + inMsg + " WHILE JOINING");
			if ( inMsg.equals("-1")){
				System.out.println("Invalid lobby number, format or full lobby, try again. \n\tRETURNING TO MAIN MENU");				
				return;
			}
			//USER HAS JOINED LOBBY SUCCESSFULLY
			playerNum = Integer.parseInt(inMsg);
			System.out.println(gameCommand + " PLAYER NUMBER: " + playerNum);
			while(! exit){
				if (playerNum == 0){
					inMsg = in.readLine();
					while (inMsg.equals("0")){
						if (! seenMSG){
							System.out.println("WAITING FOR OTHER PLAYER TO JOIN");
							seenMSG = true;
						}
						inMsg = in.readLine();	
						
					}
					readGameBoard();
					System.out.println( gameCommand + "\nMAKE MOVE: PIECE x");
					msg = stdIn.readLine();
					if (msg.toUpperCase().equals("EXIT")){
						exit = true;
						break;
						//exitRoutine(msg);
					}
					else if (inMsg.toUpperCase().equals("WINNER")){
						//SCOREBOARD
						winRoutine();
						exit = true;
						break;
					}
					out.println(msg);
					inMsg = in.readLine();
					if (inMsg.equals("EXIT")){
						exit = true;
						break;
					}
					else if (inMsg.toUpperCase().equals("WINNER")){
						//SCOREBOARD
						winRoutine();
						exit = true;
						break;
					}
					while (inMsg.equals("-1")){
						System.out.println("INVALID INPUT PLEASE TRY AGAIN");
						System.out.println(gameCommand + " " + playerNum);
						msg = stdIn.readLine();
						if (msg.toUpperCase().equals("EXIT")){
							exit = true;
							break;
						}
						else if (inMsg.toUpperCase().equals("WINNER")){
							//SCOREBOARD
							winRoutine();
							exit = true;
							break;
						}
						out.println(msg);
						inMsg = in.readLine();
					}
					if (exit){
						break;
					}
					System.out.println("WAITING FOR OTHER PLAYER");
					readGameBoard();
					inMsg = in.readLine();
					System.out.println("RECEIVED: " + inMsg + " FROM: 1");
					if (inMsg.equals("EXIT")){
						exit = true;
						break;
					}
					else if (inMsg.toUpperCase().equals("WINNER")){
						//SCOREBOARD
						winRoutine();
						exit = true;
						break;
					}
				}
				else{
					System.out.println("WAITING FOR OTHER PLAYER");
					inMsg = in.readLine();
					System.out.println("RECEIVED: " + inMsg + " FROM: 0");
					readGameBoard();
					if (inMsg.equals("EXIT")){
						exit = true;
						break;
					}
					else if (inMsg.toUpperCase().equals("WINNER")){
						//SCOREBOARD
						winRoutine();
						exit = true;
						break;
					}
					System.out.println( gameCommand + "\nMAKE MOVE: PIECE o");
					msg = stdIn.readLine();
					if (msg.toUpperCase().equals("EXIT")){
						exit = true;
						break;
					}
					else if (inMsg.toUpperCase().equals("WINNER")){
						//SCOREBOARD
						winRoutine();
						exit = true;
						break;
					}
					out.println(msg);
					inMsg = in.readLine();
					while (inMsg.equals("-1")){
						System.out.println("INVALID INPUT PLEASE TRY AGAIN");
						System.out.println(gameCommand + " " + playerNum);
						msg = stdIn.readLine();
						if (msg.toUpperCase().equals("EXIT")){
							exit = true;
							break;
						}
						else if (inMsg.toUpperCase().equals("WINNER")){
							//SCOREBOARD
							winRoutine();
							exit = true;
							break;
						}
						out.println(msg);
						inMsg = in.readLine();
					}
					
					if (exit){
						break;
					}
				}
			}
			exitRoutine("EXIT");
		}
		catch(IOException ioe){
			System.err.println("IO ERROR");
		}
	}
	
	
	/**
	* Handles the case of reading in the board from the server
	*/
	private void readGameBoard(){
		String inMsg;
		for (int i = 0; i < 18; i++){
			try{
				inMsg = in.readLine();
				System.out.println(inMsg);
			}
			catch(IOException ioe){
				System.err.println("IO ERROR");
			}
			
		}
	}
	
	/**
	* Routine for when the player won, they enter a few things and are then entered in the db on the server side
	*
	*/
	private void winRoutine(){
		String inMsg;
		try{
			System.out.println("Enter name: ");
			String msg = stdIn.readLine();
			out.println(msg);
			inMsg = in.readLine();
			if (inMsg.equals("-1")){
				System.out.println("ERROR ADDING TO SCORE BOARD EXITING");
			}
			else{
				System.out.println("ADDED SUCCEFULLY TO SCORE BOARD");
			}
			exitRoutine("EXIT");
			
		}
		catch(IOException e){
			
		}
	}
	
	/**
	* Handles the message passing if the case is REFRESH
	*
	* @param msg the message should always be refresh, possibly uneeded paramater passing
	*/
	private void refreshRoutine(String msg){
		out.println(msg);
		System.out.println("SENDING: " + msg);
		printCommands();
		try{
			int numLobbies = Integer.parseInt(in.readLine());
			System.out.println("AVAILABLE SERVERS: ");
			for (int i = 0; i < numLobbies; i++){
				System.out.println(in.readLine());
				
			}
			System.out.println("REFRESHED SERVERS");
		}
		catch(IOException ioe){
			System.out.println("IOE Exception while refreshing lobbies EXITING");
			System.exit(1);
		}
	}
	
	/**
	* Handles the message passing if the case is SCOREBOARD
	* Displays the score board db's content from the host computer sends it line by line
	*
	* @param msg the message should always be refresh, possibly uneeded paramater passing
	*/
	private void scoreBoardRoutine(String msg){
		out.println(msg);
		System.out.println("SENDING: " + msg);
		System.out.println("TOP PLAYER BASED ON NUMBER OF MOVES");
		try{
			String inMsg = in.readLine();
			while (!(inMsg.equals("DONE") || inMsg.equals("-1"))){
				if (! inMsg.equals("0")){
					System.out.println(inMsg);
				}
				inMsg = in.readLine();
			}
		}
		catch(IOException ioe){
			System.out.println("IOE Exception while reading Score Board EXITING");
			System.exit(1);
		}
		//System.out.println("FINISHED SCOREBOARD");
	}
	
	/**
	* Handles the case when the player wishes to exit the game, the game is finished, or if the other player forfeited
	* Currently just kicks out the player and doesn't ask if they want to keep playing, may be changed latter
	*
	* @param msg the message should always be exit, possibly uneeded paramater passing
	* @return returns nothing as the program exits on this methods exicution
	*/
	private void exitRoutine(String msg){
		out.println(msg);
		System.out.println("SENDING: " + msg);
		try{
			System.out.println(in.readLine());
			stdIn.close();
			in.close();
			out.close();
			socket.close();
			System.out.println("ALL CONNECTIONS CLOSED");
		}
		catch(Exception e){
			System.out.println(e);
		}
		System.exit(0);
	}

	/**
	* Prints the commands available for the user
	*
	*/
	public void printCommands(){
		System.out.println("Available Commands: ");
		System.out.println(refreshCommand + joinCommand + scoreBoardCommand + exitCommand );
	}

	
}

/**
* Main driver class that just handles the startup and command line receiveing
*/
	
public class GameClientMain{
	
	/**
	* The main driver class for this program, just handles the arguementing passing
	
	* @param args
	* String: host address/ host name
	* Integer: host port number
	*/
	
	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Invalid number of args need 2, see usage");
			System.err.println("Usage:\n \tjava client_chess.ChessClient <hostname> <port number>");
			System.err.println("\tant client <hostname> <port number>");
			System.exit(1);
		}
		
		String hostName = args[0];
		int portNumber = -1;
		System.out.println(hostName + portNumber);
		
		try {
			System.out.println( "Attempting to connect to  " + hostName + portNumber);
			portNumber = Integer.parseInt(args[1]);
			GameClient client = new GameClient(hostName, portNumber);
			client.setUpGame();
		}
		catch (NumberFormatException nfe) {
			System.err.println("Invalid port number");
			System.exit(1);
		} 
	}

}
