/*
 *	Alexander S
 *	cmpt400::assignment2
 */

import com.sun.net.httpserver.*;
import java.util.concurrent.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.imageio.*;
import java.awt.*;
import java.net.*;
import java.io.*;

/**
 *	This class is used to be able to remotly control the computer that is running this server
 *	on the local network
 */
public class RemoteMouse {

	HttpServer server;

	/**
	 *	Constructor of the RemoteMouse class
	 *	@param port the port number on the socket
	 */
	public RemoteMouse(int port) {
		try {
			//starting the server on the port provided
			this.server = HttpServer.create(new InetSocketAddress(port), 0);
			System.out.println("Running server on port: " + port + "\n");
			
			//creating contexts to process two kinds of requests
			//a request that does(n't) contain a set of coordinates
			this.server.createContext("/", new myHttpHandler());
			//a request to load the screenshot to the webpage
			this.server.createContext("/screenshot.jpg", new imageHttpHandler());
			
			//creating a pool of thereads that will handle the requests to make the server multi threaded
			this.server.setExecutor(Executors.newCachedThreadPool());
			this.server.start();
		} catch(Exception e) {
			System.out.println("Could not start server\n" +e);
			System.exit(1);
		}
	}

	/**
	 *	This class is used to perform a mouse click on the host machine
	 */
	public static class MouseClicker {
		//robot object that clicks the mouse
		Robot robo;

		/**
		 *	constructor that needs the coordinates where to click the mouse
		 *	@param x integer representing the x coordinate on the screen
		 *	@param y integer representing the y coordinate on the screen
		 */
		public MouseClicker(int x, int y) {
			int mask = InputEvent.BUTTON1_DOWN_MASK;
			//make robot instance
			try {
				robo = new Robot();
				//move the mouse to the needed location, there was a higher percision when moved multiple times
				robo.mouseMove(x, y);
				robo.mouseMove(x, y);
				robo.mouseMove(x, y);
				//perform the actual mouse click
				robo.mousePress(mask);
				robo.mouseRelease(mask);
			}
			catch(AWTException e) {
				System.out.println("Could not initialize robot or move mouse\n"+e);
			}
		}//constructor
	}

	/**
	 *	This class is used to take a screenshot of the host's screen
	 */
	public static class PrintScreen {
		//robot object
		Robot robo;
		//the screen dimensions
		public static final int width = (int)GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth();
		public static final int height = (int)GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getHeight();
		
		/**
		 *	constructor of the PrintScreen class which will generate a screenshot of host's screen
		 */
		public PrintScreen() {
			//instanciate robot
			try {
				robo = new Robot();
			}
			catch(AWTException e) {
				System.out.println(""+e);
			}

			//make a file
			File outfile = new File("screenshot.jpg");
			//capturing a screenshot using the robot object
			BufferedImage img = robo.createScreenCapture(new Rectangle(width, height));

			try {
				//trying to write the buffered image captured by the robot to the output file
				ImageIO.write(img, "jpg", outfile);
			}
			catch(IOException e) {
				System.out.println("Could not take screenshot\n"+e);
			}
		}//constructor
	}//PrintScreen

	/**
	 *	This class is used to handle the https requests for the image
	 */
	public static class imageHttpHandler implements HttpHandler {

		/**
		 *	converts image to a stream of bytes and writes it to OutputStream
		 *	@param exchange used to retrieve response headers and body
		 */
		public void handle(HttpExchange exchange) throws IOException {
			int responseCodeOK = 200;
			//this object will generate a fresh screenshot
			PrintScreen temp = new PrintScreen();

			//seting the type of content sent in this response
			Headers hr = exchange.getResponseHeaders();
			hr.add("Content-Type", "jpeg");

			//writing the bytes of the image to an array
			File file = new File ("screenshot.jpg");
			byte [] bytearray  = new byte [(int)file.length()];
			//preparing the stream to read the bytes
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			//storing the image bytes in the array
			bis.read(bytearray, 0, bytearray.length);

			//sending the response headers
			exchange.sendResponseHeaders(responseCodeOK, file.length());
			//writing the data to the output stream
			OutputStream os = exchange.getResponseBody();
			os.write(bytearray, 0, bytearray.length);
			os.close();
		}
	}

	/**
	 *	This class is used to process the request to click mouse at certain coordinates
	 */
	public static class myHttpHandler implements HttpHandler {

		/**
		 *	Adding to the OutStream the generated image map for the screenshot in order to make the image clickable
		 *	@param exchange used to retrieve response headers and body
		 */
		public void handle(HttpExchange exchange) throws IOException {
			int responseCodeOK = 200;
			//getting the screen dimension of the host
			int height = PrintScreen.height;
			int width = PrintScreen.width;

			//setting the type of content sent in this response
			Headers hr = exchange.getResponseHeaders();
			// hr.add("HTTP/1.0", "200 OK ");
			hr.add("Content-Type", "text/html");

			//getting the query from the url
			String req = exchange.getRequestURI().getQuery();

			//if the query is not empty, parse it and perform the click at the coordinates
			if(req != null) {
				//removing the slash at the end of the query to avoid format issue when parsing
				if(req.charAt(req.length()-1) == '/') {
					req = req.replace(req.substring(req.length()-1), "");
				}
				System.out.println("Query: " + req);

				//parsing the query to get the coordinates
				int[] position = parseRequest(req);
				//performing the mouse click at coordinates
				new MouseClicker(position[0], position[1]);
			}

			int offset = 15;

			//generating the image map area tags to descritize the image area into clickable regions 
			//#############
			String screenMap = "<map id=\"myMap\" name=\"screenmap\">";
			for (int i=0; i<height+1; i += offset) {
				for (int j=0; j<width+1; j += offset) {
					screenMap += "<area shape=\"react\" coords=\"" +j+ ", " +i+ ", " +(j+offset)+ ", " +(i+offset) + "\" href=\"?x=" +j+ "&y=" +i+ "\">";
				}
			}
			screenMap += "</map>\n";
			//#############

			//the DOM structure
			String response = "<html><body><img id=\"myImg\" src=\"screenshot.jpg\" height=\"" +height+ "\" width=\"" +width+ "\" usemap=\"#screenmap\">" +screenMap+ "</body></html>";

			//sending the response header
			exchange.sendResponseHeaders(responseCodeOK, response.length());
			//writing the DOM structure as a response 
			OutputStream ostream = exchange.getResponseBody();
			ostream.write(response.getBytes());
			ostream.flush();
			ostream.close();
		}//handle
	}//myHttpHandler

	/**
	 *	This method is used to parse the url query to extract the coordinates of the mouse click
	 *	@param request contains the URL which contains x and y coordinates
	 */
	public static int[] parseRequest(String request){
		//the array that will hold the coordinates
		int[] coords = new int[2];
		//splitting the query two an array of parameters
		String[] parameter = request.split("&");
		//loop through the parameters to extract the key and value pairs 
		for (int i=0; i<parameter.length; i++) {
			//getting the key and value pairs (i.e. 'X' and the 'x coordinate')
			String pair[] = parameter[i].split("=");
			//parsing the string with the coordinate and storing it in the array
			coords[i] = Integer.parseInt(pair[1]);
		}
		return coords;
	}

	/**
	 *	The main method of the program. it will start a server and process requests
	 */
	public static void main(String[] args) {
		//the remote control server 
		RemoteMouse a;
		try {
			a = new RemoteMouse(Integer.parseInt(args[0]));
		} catch(Exception e) {
			System.out.println("\nNo port was provided. Using the default port.");
			a = new RemoteMouse(9999);
		}
	}// main
}