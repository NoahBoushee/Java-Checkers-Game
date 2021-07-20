package server_game;

/*
 * Multiplayer Game Server Version
 * Works possibly change to ui and/or improvement of gameboard accessing later good enough for now
 *
 * 
 * java -cp build;lib/sqlite-jdbc-3.36.0.1.jar -Djavax.net.ssl.keyStore=gamestore -Djavax.net.ssl.keyStorePassword=password server_game.GameServerMain 4444
*/

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLServerSocket;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * @author noah.boushee
 * Server that should work for any turn based two person game such as checkers, chess, tic tac toe
 * The game session checkMove method is the only method that should be changed for different gamestore
 * @see checkMove
 */
 
class GameServer implements Runnable{
	
	private int ID;
	private int lobbyID;
	private int playerNum;
	private boolean inLobby = false;
	private boolean isRunning = false;
	private String dbLocation = "db/ScoreBoard.db";
	private SSLSocket socket = null;
	private PrintWriter out;
	private BufferedReader in;
	private ArrayList<GameSession> sessionArray;
	
	GameServer(int ID, SSLSocket socket, ArrayList<GameSession> sessionArrayIn){
		this.ID = ID;
		isRunning = true;
		sessionArray = sessionArrayIn;
		socket = socket;
		try{
			out = new PrintWriter(socket.getOutputStream(), true);                   
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			System.out.println("Input and Output setup for Client " + this.ID);
		}
		catch(IOException ioe){
			System.err.println("IO Exception in THREAD: " + ID + " WHILE SETTING UP INPUTS AND OUTPUTS");
		}
		
	}
	
	/**
	* The run method for the server, handles the inputs from a client and acts on them based on that, methods may be broken up in
	* the future similar to that of the client, only the exitRoutine is broken up so far.
	* The first player to join a server must wait for another person to join.
	*
	* @return returns nothing
	* @see exitRoutine
	*/
	public void run(){
		out.println("CONNECTED");
		out.println(ID);
		String inMsg = "", outMsg = "";
		Pattern pattern = Pattern.compile("^[a-zA-Z]+?\\s\\d+$", Pattern.CASE_INSENSITIVE);
	
		while(isRunning){
			try{
				inMsg = in.readLine().toUpperCase();
			}
			catch(IOException ioe){
				System.err.println("IO Exception in THREAD: " + ID);
				System.err.println(ioe);
				System.exit(1);
			}
			System.out.println("RECEIVED: " + inMsg + " from Client " + ID);
			if (inMsg.toUpperCase().equals("REFRESH")){
				//SEND LIST OF AVAILABLE SERVERS
				//SEND LENGTH OF ARRAYLIST THEN SEND DATA
				System.out.println("REFRESHING THE SERVERS FOR CLIENT " + ID);
				out.println(sessionArray.size());
				synchronized (sessionArray){
					for (int i = 0; i < sessionArray.size(); i++){
						out.println("\t" + sessionArray.get(i).getGameID() + " " + sessionArray.get(i).getCurrentPlayers());
					}
				}
			}
			else if (inMsg.toUpperCase().contains("JOIN")){
				//JOIN A LOBBY, ONCE HERE LOOP THROUGH TILL THE GAME IS DONE OR PLAYER GIVES UP
				Matcher matcher = pattern.matcher(inMsg);
				if (!matcher.find()){
					out.println("-1");//INVALID LOBBY
					System.out.println("SENDING -1 to CLIENT " + ID);
				}
				else{
					String temp2 = inMsg.substring(inMsg.indexOf(" ")).trim();
					lobbyID = Integer.parseInt(temp2, inMsg.length());
					if(lobbyID > sessionArray.size()){
						out.println("-1");//INVALID LOBBY
						System.out.println("SENDING -1 to CLIENT " + ID);
					}
					else{
						synchronized (sessionArray){
							if (!(sessionArray.get(lobbyID).joinLobby(this))){
								out.println("-1");
								System.out.println("SENDING -1 to CLIENT " + ID);
							}
						}
						//DO GAME THINGS HERE NOW PLAY THE GAME UNABLE TO ACCESS OTHER METHODS
						System.out.println("Thread " + ID + " JOINED LOBBY " + lobbyID);
						inLobby = true;
						playerNum = sessionArray.get(lobbyID).getPlayerNum(this);
						out.println(playerNum);
						while(true){
							if (playerNum == 0){
								//YOU GO FIRST THIS PLAYER MUST WAIT IF LOBBY ISNT FULL
								while(! sessionArray.get(lobbyID).isFull()){
									out.println("0");
									Thread t1 = Thread.currentThread();
									try{
										t1.sleep(1000);
									}
									catch(InterruptedException IE){
										System.out.println("THREAD INTERRUPTED " + ID);
										System.err.println(IE);
										System.exit(1);
									}
								}
								out.println("1");
								out.println(sessionArray.get(lobbyID).getGameBoard());
								try{
									inMsg = in.readLine();
									if (inMsg.toUpperCase().equals("EXIT")){
										exitRoutine();
										break;
									}
								}
								catch(IOException ioe){
									System.err.println("IO Exception in THREAD: " + ID);
									System.err.println(ioe);
									System.exit(1);
								}
								while (! sessionArray.get(lobbyID).checkMove(inMsg, playerNum)){
									out.println("-1");
									try{
										inMsg = in.readLine();
									}
									catch(IOException ioe){
										System.err.println("IO Exception in THREAD: " + ID);
										System.err.println(ioe);
										System.exit(1);
									}
								}
								if (inMsg.toUpperCase().equals("EXIT")){
									exitRoutine();
									break;
								}
								out.println("1");
								out.println(sessionArray.get(lobbyID).getGameBoard());
								System.out.println("Message from client " + inMsg + " In Thread: " + ID);
								sessionArray.get(lobbyID).sendMove(inMsg, playerNum);
								String temp = sessionArray.get(lobbyID).receiveMove(playerNum);
								boolean tempWait = false;
								while (temp == null || temp.equals("")){
									if (!tempWait){
										System.out.println("WAITING FOR OTHER PLAYER IN THREAD " + ID);
										tempWait = true;	
									}
									Thread t1 = Thread.currentThread();
									try{
										t1.sleep(1000);
									}
									catch(InterruptedException IE){
										System.out.println("THREAD INTERRUPTED " + ID);
										System.err.println(IE);
										System.exit(1);
									}
									temp = sessionArray.get(lobbyID).receiveMove(playerNum);
									System.out.println(temp + " " + ID);
								}
								if (temp.equals("EXIT")){
									exitRoutine();
									break;
								}
								else if (temp.toUpperCase().equals("WINNER")){
									//SCOREBOARD
									scoreBoardRoutine();
									try{
										inMsg = in.readLine();
									}
									catch(IOException e){/*IGNORED*/}
									exitRoutine();
									break;
								}
								out.println(temp);
									
							}
							else{
								//OTHER PERSON GOES FIRST
								String temp = sessionArray.get(lobbyID).receiveMove(playerNum);
								boolean tempWait = false;
								while (temp == null || temp.equals("")){
									if (!tempWait){
										System.out.println("WAITING FOR OTHER PLAYER IN THREAD " + ID);
										tempWait = true;	
									}
									Thread t1 = Thread.currentThread();
									try{
										t1.sleep(1000);
									}
									catch(InterruptedException IE){
										System.out.println("THREAD INTERRUPTED " + ID);
										System.err.println(IE);
										System.exit(1);
									}
									temp = sessionArray.get(lobbyID).receiveMove(playerNum);
									System.out.println(temp + " " + ID);
								}
								if (temp.equals("EXIT")){
									exitRoutine();
									break;
								}
								else if (temp.toUpperCase().equals("WINNER")){
									//SCOREBOARD
									scoreBoardRoutine();
									try{
										inMsg = in.readLine();
									}
									catch(IOException e){/*IGNORED*/}
									exitRoutine();
									break;
								}
								out.println(temp);
								out.println(sessionArray.get(lobbyID).getGameBoard());
								try{
									inMsg = in.readLine();
									if (inMsg.toUpperCase().equals("EXIT")){
										exitRoutine();
										break;
									}
								}
								catch(IOException ioe){
									System.err.println("IO Exception in THREAD: " + ID);
									System.err.println(ioe);
									System.exit(1);
								}
								
								while (! sessionArray.get(lobbyID).checkMove(inMsg, playerNum)){
									out.println("-1");
									try{
										inMsg = in.readLine();
									}
									catch(IOException ioe){
										System.err.println("IO Exception in THREAD: " + ID);
										System.err.println(ioe);
										System.exit(1);
									}
								
								}
								if (inMsg.toUpperCase().equals("EXIT")){
									exitRoutine();
									break;
								}
								System.out.println("Message from client " + inMsg + " In Thread: " + ID);
								out.println("1");
								//out.println(sessionArray.get(lobbyID).getGameBoard());
								sessionArray.get(lobbyID).sendMove(inMsg, playerNum);
								
							}
						}
					}
				}
				
			}
			else if (inMsg.equals("EXIT")){
				exitRoutine();
			}
			else if (inMsg.toUpperCase().contains("SCOREBOARD")){
				Connection sqlConnection = null;
				ResultSet sqlResults = null;
				Statement sqlString = null;
				try{
					
					sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
					if (sqlConnection == null){
						out.println("-1");
					}
					else{
						out.println("0");
						String sql = "CREATE TABLE IF NOT EXISTS winners (\n"  
						+ " id integer PRIMARY KEY,\n"  
						+ " name text NOT NULL,\n"  
						+ " numMoves integer\n"  
						+ ");";  
						sqlString = sqlConnection.createStatement();
						sqlString.execute(sql);
						
						sql = "SELECT * FROM winners ORDER BY numMoves";
						sqlString = sqlConnection.createStatement();
						sqlResults = sqlString.executeQuery(sql);
						String temp = "";
						while (sqlResults.next()){
							out.println(sqlResults.getInt("id") + " " + sqlResults.getString("name") + " " + sqlResults.getInt("numMoves"));
						}
						out.println("DONE");
						//System.out.println("SENDING: DONE FROM: " + ID);
					}
				}
				catch(SQLException sqle){
					System.out.println("SQL Exception in thread " + ID);
					System.out.println(sqle);
					out.println("-1");
				}
				finally{
					if (sqlResults != null){
						 try {
							sqlResults.close();
						} 
						catch (SQLException e) { /* Ignored */}
					}
					if (sqlString != null){
						 try {
							sqlString.close();
						} 
						catch (SQLException e) { /* Ignored */}
					}
					if (sqlConnection != null){
						 try {
							sqlConnection.close();
						} 
						catch (SQLException e) { /* Ignored */}
					}
				}
			}
			else{
				System.out.println("SENDING TO CLIENT WITH ID: " + ID + " INVALID");
				out.println("INVALID");
				//PLAYER PICKED AN INVALID COMMAND
			} 
		}
	}
	
	/**
	* Handles the case where the user wins, calls exitRoutine after as they are done
	*
	* @return returns nothing
	*/
	public void scoreBoardRoutine(){
		Connection sqlConnection = null;
		PreparedStatement sqlString = null;
		try{
			out.println("WINNER");
			String name = in.readLine();
			if (name.equals("") || name.equals(null)){
				out.println("-1");
			}
			else{
				out.println("0");
				sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
				String sql = "INSERT INTO winners(name, numMoves)VALUES(?,?)";
				sqlString = sqlConnection.prepareStatement(sql);
				sqlString.setString(1, name);
				sqlString.setInt(2, sessionArray.get(lobbyID).getCurrentNumMoves());
				sqlString.executeUpdate();
			}
			
		}
		catch(IOException | SQLException e){
			System.out.println("EXCEPTION OCCURED IN THREAD: " + ID);
			System.out.println(e);
		}
		finally{
			if (sqlString != null){
				try {
					sqlString.close();
				} 
				catch (SQLException e) { /* Ignored */}
			}
			if (sqlConnection != null){
				try {
					sqlConnection.close();
				} 
				catch (SQLException e) { /* Ignored */}
			}
		}
	}
	
	/**
	* Handles the case when the player wishes to exit the game, the game is finished, or if the other player forfeited
	* Currently just kicks out the player and doesn't ask if they want to keep playing, may be changed latter
	* Closes the connections to the client if they aren't already closed
	*
	* @return returns nothing
	*/
	public void exitRoutine(){
		try{
			out.println("CLOSED");
			System.out.println("CLOSING CONNECTIONS TO CLIENT " + ID);
			isRunning = false;
			if (inLobby){
				inLobby=false;
				sessionArray.get(lobbyID).sendMove("EXIT", playerNum);
				sessionArray.get(lobbyID).leaveLobby(playerNum);
			}
			in.close();
			out.close();
			socket.close();
			System.out.println("CLOSED CONNECTIONS TO CLIENT " + ID);
		}
		catch(Exception e){
			System.out.println("CONNECTIONS ALREADY CLOSED TO CLIENT: " + ID);
		}
	}
	
	/**
	* @return ID of this thread
	*/
	public int getID(){
		return ID;
	}
	
}	
/**
* This class handles the communication between the two clients
* This class will need some rewriting if doing similar games, chess, tic tac toe, etc
* The board would be easier to work with if it was a 2D array instead of a String but it's a bit late now
*/
class GameSession{
	//NEEDS WORK ADD METHOD AND VARIABLE FOR THE NUMBER OF MESSAGES THAT NEED TO BE SENT BACK TO EACH CLIENTS
	private int gameID;
	private int totalMoves = 0;
	private int maxPlayers = 2;
	private int currentPlayers = 0;
	private int numMessages = 0;
	private boolean isFull = false;
	private ArrayList<GameServer> playerArray = new ArrayList<GameServer>(maxPlayers);
	private String move0 = "";
	private String move1 = "";
	private String messagesToSend = "";
	private Pattern pattern = Pattern.compile("^[A-H][1-8]\\s[A-H][1-8]$", Pattern.CASE_INSENSITIVE);
	//Possibly make the board a config value/make config file
	private String gameBoard = "  +-+-+-+-+-+-+-+-+ \n" +
							   "1 |o| |o| |o| |o| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "2 | |o| |o| |o| |o| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "3 |o| |o| |o| |o| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "4 | | | | | | | | | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "5 | | | | | | | | | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "6 | |x| |x| |x| |x| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "7 |x| |x| |x| |x| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "8 | |x| |x| |x| |x| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "   A B C D E F G H    ";    //18 long
	private String piece0 = "x";
	private String piece0Crowned = "X";
	private String piece1 = "o";
	private String piece1Crowned = "O";
	private String[] arrayLetters = {"A", "B", "C", "D", "E", "F", "G", "H"};
	private String[] arrayNumbers = {"1", "2", "3", "4", "5", "6", "7", "8"};
	
	GameSession(int ID){
		gameID = ID;
	}
	
	/**
	* Determines whether or not the move that the player is making is valid, has numerous helper methods, check @see
	* Also marks the board with respects to moving pieces from one spot to another and removing jumped pieces
	* This method may need more cleaning up later but it should be fine for now
	* ADD A CHECK FOR IF THE GAME IS DONE, NO MORE PIECES ON ONE SIDE POSSIBLY SQL CONNECTION FOR THE WINNER WHERE THEY CAN ENTER NAME AND STUFF
	* IMPLEMENT THE SQL THING
	* @param move The move that the player is trying to make, unbroken up so it's along the lines of LetterNumber LetterNumber assuming the user gave correct inputs
	* @param playerNum What number the player is 0 or 1
	* @return returns boolean value if move is correct
	* @see checkMoveDoubleJumpMiddle
	* @see findBetweenLetter
	* @see computeValue
	*/
	
	protected synchronized boolean checkMove(String move, int playerNum){
		//ADD MORE TO THIS LATER FOR NOW IT RETURNS TRUE 
		//THIS WILL CHECK IF THE MOVE IS AVILABLE IF IT IS VALID ETC
		//HAVE THIS IMPLEMENT DIFFERENT GAMES THROUGH CREATING OF DIFFERENT GAME OBJECTS
		
		if (move.equals("EXIT")){
			return true;
		}
		//MAY NEED TO MOVE THIS AROUND
		if(! (gameBoard.contains("X") || gameBoard.contains("x"))){
			//O WINS
			move0 = "EXIT";
			move1 = "WINNER";
			return true;
		}
		else if(! (gameBoard.contains("O") || gameBoard.contains("o"))){
			//X WINS
			move1 = "EXIT";
			move0 = "WINNER";
			return true;
		}
		move = move.toUpperCase().trim();
		System.out.println(move + " in lobby " + gameID);
		Matcher matcher = pattern.matcher(move);
		if (!matcher.find()){
			System.out.println("INVALID MATCHER CASE in lobby " + gameID);
			return false;
			
		}
		String[] splitMoves = move.split("\\s");
		int moveNum0 = computeValue(splitMoves[0], 0);
		int moveNum1 = computeValue(splitMoves[1], 0);
		moveNum0 += computeValue(splitMoves[0], 1);
		moveNum1 += computeValue(splitMoves[1], 1);
		if (moveNum0 == moveNum1){
			return false;
		}
		
		String currentPiece = String.valueOf(gameBoard.charAt(moveNum0));
		System.out.println(moveNum0 + " start position");
		System.out.println(moveNum1 + " end position");
		System.out.println(currentPiece + " start piece");
		if ((currentPiece.equals("x") || currentPiece.equals("X")) && playerNum == 1){
			//Case where wrong player tries to move pieces
			System.out.println("WRONG PLAYER TRYING TO MOVE PIECE");
			return false;
		}
		if ((currentPiece.equals("o") || currentPiece.equals("O")) && playerNum == 0){
			//Case where wrong player tries to move pieces
			System.out.println("WRONG PLAYER TRYING TO MOVE PIECE");
			return false;
		}
		//COVERS BACKWARDS CASE IF NOT KING
		if (currentPiece.equals("x") && moveNum0 < moveNum1){
			//GOING BACK WHEN NOT KING
			System.out.println("Piece going back in lobby " + gameID);
			return false;
		}
		else if (currentPiece.equals("o") && moveNum0 > moveNum1){
			//GOING BACKWARDS WHEN NOT KING
			System.out.println("Piece going back in lobby " + gameID);
			return false;
		}
		else if (currentPiece.equals(" ") || currentPiece.equals("+") || currentPiece.equals("|")){
			//Not a piece there
			return false;
		}
		if(! String.valueOf(gameBoard.charAt(moveNum1)).equals(" ")){
			//PIECE ALREADY AT SPOT TO MOVE TO
			System.out.println("End position blocked in lobby " + gameID);
			return false;
		}
		//KINGS THE PIECE AND LETS IT IGNORE LOGIC THAT PREVENTS IT FROM GOING BACKWARDS
		if (currentPiece.equals("x") && splitMoves[1].contains("1")){
			//KINGED PIECE MAKE ANNOUNCEMENT OR SOMETHINg
			currentPiece = "X";
		}
		else if (currentPiece.equals("o") && splitMoves[1].contains("8")){
			currentPiece = "O";
		}
		//CHECK CASE FOR MOVING TO NONE DIAGNOLS
		//LOGIC FOR REMOVING OPPPOSITIONS PIECES HERE
		int tempLetter = computeValue(splitMoves[0], 0)- computeValue(splitMoves[1], 0);
		int tempNumber = computeValue(splitMoves[0], 1)- computeValue(splitMoves[1], 1);
		System.out.println(tempLetter + " " + tempNumber);
		if (Math.abs(tempNumber) >= 84){
			//JUMPING ATLEAST 1 CHECKER
			//int tempNumberJumpCheck = computeValue(splitMoves[0], 1)- computeValue(splitMoves[1], 1);
			//MATHS 168 For Double 
			if (Math.abs(tempNumber) == 252){
				//TRIPLE JUMP FUCKING MAD MAN
				//SORRY BUT I DO NOT WANT TO CODE THIS
				System.out.println("TRIPLE JUMP");
				return false;
				
			}
			//USE LOGIC FROM SINGLE MOVE OR SINGLE JUMP TO CALCULATE THESE
			else if (Math.abs(tempNumber) == 168){
				//DOUBLE JUMP
				System.out.println("DOUBLE JUMP");
				int position1 = Arrays.asList(arrayLetters).indexOf(String.valueOf(splitMoves[0].charAt(0)));
				int position2 = Arrays.asList(arrayLetters).indexOf(String.valueOf(splitMoves[1].charAt(0)));
				int position3 = Arrays.asList(arrayNumbers).indexOf(String.valueOf(splitMoves[0].charAt(1)));
				int position4 = Arrays.asList(arrayNumbers).indexOf(String.valueOf(splitMoves[1].charAt(1)));
				System.out.println(position1 + " " + position2  + " " + position3 + " " + position4);
				if (position1 == position2){
					//END AT SAME COLUMN
					try{
						//ADDCASE FOR IF FIRST MOVE ISNT GOOD
						if(String.valueOf(splitMoves[0].charAt(0)).equals("A") || String.valueOf(splitMoves[0].charAt(0)).equals("B") || String.valueOf(splitMoves[0].charAt(0)).equals("H") || String.valueOf(splitMoves[0].charAt(0)).equals("G")){
							int tempNum;
							if(position3 < position4){
								tempNum = 1;
							}
							else{
								tempNum = -1;
							}
							int tempPos1 = position3 + tempNum;
							String temp = "";
							if (String.valueOf(splitMoves[0].charAt(0)).equals("A")){
								temp = "B";
							}
							else if (String.valueOf(splitMoves[0].charAt(0)).equals("B")){
								temp = "C";
							}
							else if (String.valueOf(splitMoves[0].charAt(0)).equals("H")){
								temp = "G";
							}
							else{
								temp = "F";
							}
							String move1 = temp + arrayNumbers[tempPos1];
							int remove3 = computeValue(move1, 0);
							remove3 += computeValue(move1, 1);
							if(String.valueOf(gameBoard.charAt(remove3)).equals(" ") || String.valueOf(gameBoard.charAt(remove3)).toUpperCase().equals(currentPiece.toUpperCase())){
								return false;
							}
							int tempPos2 = tempPos1;
							String move2 = splitMoves[0].charAt(0) + arrayNumbers[tempPos2];
							int remove4 = computeValue(move2, 0);
							remove4 += computeValue(move2, 1);
							if(String.valueOf(gameBoard.charAt(remove4)).equals(" ") || String.valueOf(gameBoard.charAt(remove4)).toUpperCase().equals(currentPiece.toUpperCase())){
								return false;
							}
							gameBoard = gameBoard.substring(0, remove4) + " " + gameBoard.substring(remove4 + 1);
							gameBoard = gameBoard.substring(0, remove3) + " " + gameBoard.substring(remove3 + 1);
							//MESSAGE THAT 2 JUMPED
						}
						else{
							System.out.println("MIDDLE DOUBLE JUMP SAME COLUMN");
							if(String.valueOf(splitMoves[0].charAt(0)).equals("C")){
								if (! checkMoveDoubleJumpMiddle(position1, position2, position3, position4, "D", "B", currentPiece)){
									return false;
								}
							}
							else if(String.valueOf(splitMoves[0].charAt(0)).equals("D")){
								if (! checkMoveDoubleJumpMiddle(position1, position2, position3, position4, "C", "E", currentPiece)){
									return false;
								}
							}
							else if(String.valueOf(splitMoves[0].charAt(0)).equals("E")){
								if (! checkMoveDoubleJumpMiddle(position1, position2, position3, position4, "F", "D", currentPiece)){
									return false;
								}
							}
							else if(String.valueOf(splitMoves[0].charAt(0)).equals("F")){
								if (! checkMoveDoubleJumpMiddle(position1, position2, position3, position4, "G", "E", currentPiece)){
									return false;
								}
							}
						}
					}
					catch(Exception badMove){
						//PRetty much just a index out of bounds catch
						return false;
					}
				}
				else{
					
					int removePos1, removePos2, removePos3, removePos4;
					int tempNum = 0, tempNum2 = 0;
					if (position1 < position2){
						tempNum = 1;
					}
					else{
						tempNum = -1;
					}
					if (position3 < position4){
						tempNum2 = 1;
					}
					else{
						tempNum2 = -1;
					}
					//These are the positions of the pieces to be removed from the board with a diagnol double jump
					//Two values are added to Pos2 and Pos4 as they are the second and need to get further to find the jump spot
					removePos1 = position1 + tempNum;
					removePos2 = removePos1 + tempNum + tempNum;
					removePos3 = position3 + tempNum2;
					removePos4 = removePos3 + tempNum2 + tempNum2;
					
					String remove1 = arrayLetters[removePos1] + arrayNumbers[removePos3];
					String remove2 = arrayLetters[removePos2] + arrayNumbers[removePos4];
					int removeValDouble1 = computeValue(remove1, 0);
					removeValDouble1 += computeValue(remove1, 1);
					int removeValDouble2 = computeValue(remove2, 0);
					removeValDouble2 += computeValue(remove2, 1);
					//CANT JUMP NO PIECE THERE
					if(String.valueOf(gameBoard.charAt(removeValDouble1)).equals(" ") || String.valueOf(gameBoard.charAt(removeValDouble1)).toUpperCase().equals(currentPiece.toUpperCase())){
						return false;
					}
					if(String.valueOf(gameBoard.charAt(removeValDouble2)).equals(" ")|| String.valueOf(gameBoard.charAt(removeValDouble2)).toUpperCase().equals(currentPiece.toUpperCase())){
						return false;
					}
					gameBoard = gameBoard.substring(0, removeValDouble1) + " " + gameBoard.substring(removeValDouble1 + 1);
					gameBoard = gameBoard.substring(0, removeValDouble2) + " " + gameBoard.substring(removeValDouble2 + 1);
				}
				
			}
			else if ((Math.abs(tempLetter) == 4 && Math.abs(tempNumber) == 84)){
				System.out.println("SINGLE JUMP");
				//FIGURE OUT HOW TO FIND THE GAP WHERE IT WILL BE JUMPING OVER THE OTHER PIECE
				//USE DOCUMENT ME METHOD
				String removeLetter = findBetweenLetter(splitMoves[0], splitMoves[1]);
				if (removeLetter.equals(-1)){
					//SHOULDNT HAPPEN
					return false;
				}
				String removeNumber;
				if (Integer.parseInt(String.valueOf(splitMoves[0].charAt(1))) < Integer.parseInt(String.valueOf(splitMoves[1].charAt(1)))){
					removeNumber = String.valueOf(Integer.parseInt(String.valueOf(splitMoves[0].charAt(1))) + 1);
				}
				else if (Integer.parseInt(String.valueOf(splitMoves[0].charAt(1))) > Integer.parseInt(String.valueOf(splitMoves[1].charAt(1)))){
					removeNumber = String.valueOf(Integer.parseInt(String.valueOf(splitMoves[0].charAt(1))) - 1);
				}
				else{
					return false;
				}
				String removePiecePosition = removeLetter + removeNumber;
				int removeVal = computeValue(removePiecePosition, 0);
				removeVal += computeValue(removePiecePosition, 1);
				if (String.valueOf(gameBoard.charAt(removeVal)).equals(" ")|| String.valueOf(gameBoard.charAt(removeVal)).toUpperCase().equals(currentPiece.toUpperCase())){
					return false;
				}
				//String removedPiece = gameBoard.charAt(moveNum0);
				//messageToSend =  messageToSend + "Piece " + removedPiece +  " at position "+removePiecePosition + " was jumped\n"; 
				gameBoard = gameBoard.substring(0, removeVal) + " " + gameBoard.substring(removeVal + 1);
			}
			else{
				return false;
			}			
		}
		else if ((Math.abs(tempLetter) == 2 && Math.abs(tempNumber) == 42)){
			System.out.println("REGULAR Move");
			//System.out.println(tempVal);
			//VALID FOR ONLY X UNCOMMENT
			//subtracting and doing modular division shoudl result in 0? 4?
			//42
			
		}
		else{
			System.out.println("BAD CASE");
			return false;
		}
		//MARK BOARD WITH CURRENTPIECE AND RETURN TRUE
		gameBoard = gameBoard.substring(0, moveNum0) +    " "       + gameBoard.substring(moveNum0 + 1);
		gameBoard = gameBoard.substring(0, moveNum1) + currentPiece + gameBoard.substring(moveNum1 + 1);
		System.out.println(gameBoard);
		totalMoves +=1;
		return true;
	}
	
	/**
	* Helper method for checkMove
	* Handles the double jump cases with two possibilities IE C D E F
	* @param position1 The computed value of move1 letter part
	* @param position2 The computed value of move1 number part
	* @param position3 The computed value of move2 letter part
	* @param position4 The computed value of move2 number part
	* @param temp1 The first letter
	* @param temp2 The second letter
	* @param currentPiece the piece at the start position, will always be a piece as it won't reach here without one
	* @return returns true or false if it is a valid moves, removes the jumped pieces if the move is valid
	* @see checkMove()
	*/
	protected boolean checkMoveDoubleJumpMiddle(int position1, int position2, int position3, int position4, String temp1, String temp2, String currentPiece){
		//temp1 = D G F C
		//temp2 = B E D E
		int totalChecks = 0;
		int val = 0, val2 = 0;
		String temp = temp1;
		if(position3 < position4){
			val = 1;
			val2 = 3;
		}
		else{
			val = -1;
			val2 = -3;
		}
		int tempPos1 = position3 + val;
		String move1 = temp + arrayNumbers[tempPos1];
		System.out.println("TEMPMOVE1 " + move1);
		int remove1 = computeValue(move1, 0);
		remove1 += computeValue(move1, 1);
		System.out.println(gameBoard.charAt(remove1));
		if(!(String.valueOf(gameBoard.charAt(remove1)).equals(" ") || String.valueOf(gameBoard.charAt(remove1)).toUpperCase().equals(currentPiece.toUpperCase()))){
			totalChecks +=1;
		}
		else{
			temp = temp2;
			tempPos1 = position3 + val;
			move1 = temp + arrayNumbers[tempPos1];
			System.out.println("TEMPMOVE1BACKUP " + move1);
			remove1 = computeValue(move1, 0);
			remove1 += computeValue(move1, 1);
			if(String.valueOf(gameBoard.charAt(remove1)).equals(" ") || String.valueOf(gameBoard.charAt(remove1)).toUpperCase().equals(currentPiece.toUpperCase())){
				return false;
			}
		}
		
		if (totalChecks == 1){
			//MOVE FROM POSITION B
			temp = temp1;
		}
		else{
			temp = temp2;
		}
		tempPos1 = position3 + val2;
		move1 = temp + arrayNumbers[tempPos1];
		System.out.println("TEMPMOVE2 " + move1);
		int remove2 = computeValue(move1, 0);
		remove2 += computeValue(move1, 1);
		if(String.valueOf(gameBoard.charAt(remove2)).equals(" ") || String.valueOf(gameBoard.charAt(remove2)).toUpperCase().equals(currentPiece.toUpperCase())){
			return false;
		}
		gameBoard = gameBoard.substring(0, remove1) +    " "       + gameBoard.substring(remove1 + 1);
		gameBoard = gameBoard.substring(0, remove2) +    " "       + gameBoard.substring(remove2 + 1);
		System.out.println("GOT TO END");
		return true;
	}
	
	/**
	* Finds the letters between a single jump position
	* @param start The start position, what piece the user wants to move
	* @param end The end position, the position where the user want to end up
	* @return Returns the string value of the letter between the two positions, returns -1 if none match
	*/
	protected String findBetweenLetter(String start, String end){
		switch(start.charAt(0)){
			case 'A':
				return "B";
			case 'B':
				return "C";
			case 'C':
				if (end.contains("E")){
					return "D";
				}
				else{
					return "B";
				}
			case 'D':
				if (end.contains("F")){
					return "E";
				}
				else{
					return "C";
				}
			case 'E':
				if (end.contains("G")){
					return "F";
				}
				else{
					return "D";
				}
			case 'F':
				if (end.contains("H")){
					return "G";
				}
				else{
					return "E";
				}
			case 'G':
				return "F";
			case 'H':
				return "G";
		}
		return "-1";
	}
	
	
	/**
	* Computes the position to where the piece is in the string(board)
	*
	* @param position receives the position of the piece or the position to where the piece will be move
	* @param mode which mode to select, number or letter
	* @return returns the computed value
	*/
	//COMPUTATION WORKS
	protected int computeValue(String position, int mode){
		int temp = 0;
		if (mode == 0){
			switch(position.charAt(0)){
			case 'A':
				temp += 4;
				break;
			case 'B':
				temp += 6;
				break;
			case 'C':
				temp += 8;
				break;
			case 'D':
				temp += 10;
				break;
			case 'E':
				temp += 12;
				break;
			case 'F':
				temp += 14;
				break;
			case 'G':
				temp += 16;
				break;
			case 'H':
				temp += 18;
				break;
			}
		}
		else{
			switch(position.charAt(1)){
			case '1':
				temp += 20;
				break;
			case '2':
				temp += 62;
				break;
			case '3':
				temp += 104;
				break;
			case '4':
				temp += 146;
				break;
			case '5':
				temp += 188;
				break;
			case '6':
				temp += 230;
				break;
			case '7':
				temp += 272;
				break;
			case '8':
				temp += 314;
				break;
			}
		}
		return temp;
	}
	
	/**
	* Has the player join the lobby, be inserted into the arraylist
	* 
	* @param receives a player object
	* @return returns boolean value if successfully joined lobby
	*/
	protected boolean joinLobby(GameServer player){
		if(isFull){
			return false;
		}
		playerArray.add(player);
		currentPlayers  += 1;
		if (currentPlayers == maxPlayers){
			isFull = true;
		}
		return true;
	}
	/**
	* Has the player leave the lobby, be removed from the arraylist
	* 
	* @param receives a player number
	* @return returns nothing
	*/
	protected void leaveLobby(int playerNum){
		playerArray.set(playerNum, null);
		currentPlayers -= 1;
		if (isFull){
			isFull = false;
			gameBoard =        "  +-+-+-+-+-+-+-+-+ \n" +
							   "1 |o| |o| |o| |o| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "2 | |o| |o| |o| |o| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "3 |o| |o| |o| |o| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "4 | | | | | | | | | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "5 | | | | | | | | | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "6 | |x| |x| |x| |x| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "7 |x| |x| |x| |x| | \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "8 | |x| |x| |x| |x| \n" +
							   "  +-+-+-+-+-+-+-+-+ \n" +
							   "   A B C D E F G H    ";
		}
		move0 = null;
		move1 = null;
		
	}
	
	/**
	* Sends a move to the game session, this handles "receiving" the move to the other player
	* 
	* @param move the move that is to be receive by the other player
	* @param playerNum receives the players number, 0 or 1
	* @return returns the move from the other player
	*/
	protected void sendMove(String move, int playerNum){ //MARK BOARD HERE TOO NO BOARD YET
		if (move.equals("EXIT") && playerNum == 0){
			System.out.println("SENDING WINNER FOR PLAYER 1");
			move0 = "WINNER";
		}
		else if (move.equals("EXIT") && playerNum == 1){
			System.out.println("SENDING WINNER FOR PLAYER 0");
			move1 = "WINNER";
		}
		else if (playerNum == 0){ ///SEND TO PLAYER 1
			move0 = move;
		}
		else if (playerNum == 1){ 
			move1 = move;
		}
	}
	/**
	* Assigns a move to the game session, this handles "sending" the move to the other player
	* 
	* @param playerNum receives the players number, 0 or 1
	* @return returns the move from the other player
	*/
	protected String receiveMove(int playerNum){ 
		if (playerNum == 0){ ///SEND TO PLAYER 1
			String temp = move1;
			move1 = null;
			return temp;
		}
		else{ 
			String temp = move0;
			move0 = null;
			return temp;
		}
	}
	/**
	* Assigns the player a number 0 or 1 
	*
	* @param player is given a GameServer object, can get the players ID from that and get their lobby number
	* @return the current players number, shouldn't get -1
	*/
	protected int getPlayerNum(GameServer player){
		for (int i = 0; i < playerArray.size(); i ++){
			try{
				if (playerArray.get(i).getID() == player.getID()){
					return i;
				}
			}
			catch(Exception lobbyE){
				//DO NOTHING
				//ARRAYLIST OUT OF BOUNDS SHOULDN'T HAPPEN
			}
		}
		return -1;
	}
	/**
	* @return returns a boolean value of the lobbies current fullness
	*/
	public boolean isFull(){
		return isFull;
	}
	/**
	* @return current number of players in lobby
	*/
	public int getCurrentPlayers(){
		return currentPlayers;
	}
	/**
	* @return game lobby ID
	*/
	public int getGameID(){
		return gameID;
	}
	/**
	* @return returns games board
	*/
	public String getGameBoard(){
		return gameBoard;
	}
	/**
	* @return returns number of moves in the game, resets it as well as this method is only called at the end of a game
	*/
	public int getCurrentNumMoves(){
		int temp = totalMoves;
		totalMoves = 0;
		return temp;
	}
	
}
/**
* Main driver class that just handles the startup and command line receiveing
*/	
 public class GameServerMain {	

	/**
	 * driver program for server, creates threads, lobbies, and gets them started and clears them once a client disconnects
	 *
	 * @param args
	 * Integer: host port number
	 */

	public static void main(String[] args) {
		
		if (args.length != 1){
			System.err.println("Invalid number of args need 1, see usage");
			System.err.println("Usage:\n \tjava server_game.GameServer <port number>");
			System.err.println("\tant server <port number>");
			System.exit(1);
		}
		
		Properties prop = new Properties();
		String fileName = "config/server.config";
		try (FileInputStream fis = new FileInputStream(fileName)) {
			prop.load(fis);
		} 
		catch (FileNotFoundException ex) {System.out.println("FILE NOT FOUND"); System.exit(1);} 
		catch (IOException ex) {System.out.println("IOEXCEPTION"); System.exit(1);}
		
		int maxConnectedClients = Integer.parseInt(prop.getProperty("server.maxClients"));
		int currentConnectedClients = 0;
		int portNumber = -1;
		ArrayList<GameServer> runnablesArray = new ArrayList<GameServer>(maxConnectedClients);
		ArrayList<Thread> threadArray = new ArrayList<Thread>(maxConnectedClients);
		
		ArrayList<GameSession> sessionArray = new ArrayList<GameSession>(maxConnectedClients/2);
		for (int i = 0; i < maxConnectedClients; i++){
			runnablesArray.add(null);
			threadArray.add(null);
		}
		System.out.println("CREATED " + maxConnectedClients + " THREADS AND RUNNABLES");
		for (int i = 0; i < (maxConnectedClients/2); i++){
			sessionArray.add(new GameSession(i));
		}
		System.out.println("CREATED " + maxConnectedClients/2 + " GAME SESSIONS");
		try{
			portNumber = Integer.parseInt(args[0]);
			SSLServerSocketFactory factory = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
			SSLServerSocket serverSocket = (SSLServerSocket)factory.createServerSocket(portNumber);
            System.out.println("The server is listening at: " + 
                    serverSocket.getInetAddress() + " on port " + 
                    serverSocket.getLocalPort());
			while (true){
				if (currentConnectedClients < maxConnectedClients){
					SSLSocket clientSocket = (SSLSocket)serverSocket.accept(); 
					System.out.println("ServerSocket accepted");
					for (int i = 0; i < maxConnectedClients; i++){
						if(runnablesArray.get(i) == null){
							System.out.println("CREATED THREAD WITH ID " + i);
							runnablesArray.add(i, new GameServer(i, clientSocket, sessionArray));
							threadArray.add(i, new Thread(runnablesArray.get(i)));
							threadArray.get(i).start();
							currentConnectedClients += 1;
							break;
						}
						else{
							try{
								if(!(threadArray.get(i).isAlive())){
									threadArray.set(i, null);
									runnablesArray.set(i, null);
									currentConnectedClients -= 1;
									System.out.println("DESTROYED THREAD WITH ID " + i);
								}
							}
							catch (Exception e2){
								System.out.println("Error clearing thread, probably already  cleared. " + e2);
							}
						}
					}
					
				}
				else{
					for (int i = 0; i < maxConnectedClients; i++){
						try{
								if(!(threadArray.get(i).isAlive())){
									threadArray.set(i, null);
									runnablesArray.set(i, null);
									currentConnectedClients -= 1;
									System.out.println("DESTROYED THREAD WITH ID " + i);
								}
							}
							catch (Exception e3){
								System.out.println("Error clearing thread, probably already  cleared." + e3);
							}
					}
				}
				
			}
		}
		catch(NumberFormatException nfe){
			System.err.println(portNumber);
			System.err.println(args[0]);
			System.err.println("Invalid port number");
			System.exit(1);
		}
		catch (IOException ioe) {
			System.err.println("" + ioe.getMessage());
			System.exit(1);						
		}
	}

}
