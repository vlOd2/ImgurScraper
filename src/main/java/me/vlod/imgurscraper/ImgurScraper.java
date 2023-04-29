package me.vlod.imgurscraper;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.JOptionPane;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import me.vlod.imgurscraper.console.Console;
import me.vlod.imgurscraper.discord.webhook.DiscordWebhook;
import me.vlod.imgurscraper.logger.Logger;

public class ImgurScraper implements Runnable {
	public static ImgurScraper instance;
	public static Logger logger;
	public static String unavailablePhoto;
	public boolean running;
	public Console console;
	public CommandHandler commandHandler;
	public String outputFolderBase;
	public boolean noSaving;
	public boolean noDownload;
	public String linkIDFormat = "rrrrr";
	public DiscordWebhook discordWebhook;

	static {
		// Logger setup
		logger = new Logger();

		// Console target
		logger.targets.add(new Delegate() {
			@Override
			public void call(Object... args) {
				String str = (String) args[0];
				Color color = (Color) args[1];
				
				if (instance != null && instance.console != null) {
					instance.console.write(str, color);
				}
			}
		});
		
		// Stdout and Stderr target
		logger.targets.add(new Delegate() {
			@Override
			public void call(Object... args) {
				String str = (String) args[0];
				Color color = (Color) args[1];
				
				if (color != Color.red) {
					System.out.println(str);
				} else {
					System.err.println(str);
				}
			}
		});
		
		try {
		    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		    InputStream inputStream = ImgurScraper.class.getResourceAsStream("/Unavailable.png");
		    
		    byte[] buffer = new byte[4096];
		    int readAmount;
		    
		    while ((readAmount = inputStream.read(buffer)) > 0) {
		    	outputStream.write(buffer, 0, readAmount);
		    }
		    
		    byte[] data = outputStream.toByteArray();
		    inputStream.close();
		    outputStream.close();
		    
		    unavailablePhoto = Base64.getEncoder().encodeToString(data);
		} catch (Exception ex) {
			logger.throwable(ex);
			System.exit(1);
		}
	}
	
	public String[] generateIDs(int amount) {
		String chars = "abcdefghijklmnoprqstuwvxyz";
		String bigChars = chars.toUpperCase();
		ArrayList<String> ids = new ArrayList<String>();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		
		for (int i = 0; i < amount; i++) {
			String id = "";
			
			for (char chr : this.linkIDFormat.toCharArray()) {
				if (chr == 'c') {
					id += chars.charAt(random.nextInt(0, chars.length()));
				} else if (chr == 'C') {
					id += bigChars.charAt(random.nextInt(0, bigChars.length()));
				} else if (chr == 'i') {
					id += "" + random.nextInt(0, 10);
				} else if (chr == 'r') {
					int chance = random.nextInt(0, 3);

					if (chance == 0) {
						id += bigChars.charAt(random.nextInt(0, bigChars.length()));
					} else if (chance == 1) {
						id += chars.charAt(random.nextInt(0, chars.length()));
					} else {
						id += "" + random.nextInt(0, 10);
					}
				}
			}
			
			ids.add(id);
		}
		
		return ids.toArray(new String[0]);
	}
	
	public boolean downloadImage(String url, String outputFilePath) {
		try {
			Response resultImageResponse = Jsoup.connect(url).ignoreContentType(true).execute();
			byte[] data = resultImageResponse.bodyAsBytes();
			
			if (Base64.getEncoder().encodeToString(data).equalsIgnoreCase(unavailablePhoto)) {
				throw new HttpStatusException("Not Found", 404, url);
			}
			
			FileOutputStream imageStream = new FileOutputStream(new File(outputFilePath));
			imageStream.write(data);
			imageStream.close();
			return true;
		} catch (Exception ex) {
			if (ex instanceof HttpStatusException && 
				((HttpStatusException)ex).getStatusCode() == 404) {
				logger.error("Link \"%s\" doesn't exist!", url);
			} else {
				logger.error("Unable to download the image from the link \"%s\"!", url);
				ex.printStackTrace();
			}
			return false;
		}
	}
	
	public void handleInput(String input) {
		if (input.length() < 1) return;
		
		String[] inputSplitted = Utils.splitBySpace(input);
		String cmd = inputSplitted[0];
		String[] arguments = new String[inputSplitted.length - 1];
		System.arraycopy(inputSplitted, 1, arguments, 0, arguments.length);
		
        for (int argIndex = 0; argIndex < arguments.length; argIndex++) {
            String arg = arguments[argIndex];

            if (arg.startsWith("\"")) {
            	arg = arg.substring(1, arg.length() - 1);
            }
                
            if (arg.endsWith("\"")) {
            	arg = arg.substring(0, arg.length() - 1);
            }
            
            arguments[argIndex] = arg;
        }

        Delegate handleInputDelegate = new Delegate() {
			@Override
			public void call(Object... args) {
				commandHandler.doCommand(cmd, arguments);
			}
        };
        
        if (this.console != null) {
            new Thread("Handle-Input-Delegate") {
            	@Override
            	public void run() {
            		handleInputDelegate.call();
            	}
            }.start();
        } else {
        	handleInputDelegate.call();
        }
	}
	
	public void printStartupMessage() {
		logger.info("Welcome to ImgurScraper!");
		logger.info("Type \"help\" for more information");
		logger.warn("WARNING: The photos you generate may contain pornography"
				+ " or other inappropriate imagery. Use with caution");
	}
	
	@Override
	public void run() {
		this.running = true;
		this.commandHandler = new CommandHandler(this);
	
		Scanner inputScanner = null;
		if (System.console() != null) {
			inputScanner = new Scanner(System.in);
		}

		if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
			this.console = new Console();
			this.console.onSubmit = new Delegate() {
				@Override
				public void call(Object... args) {
					String input = (String)args[0];
					if (input.length() < 1) {
						JOptionPane.showMessageDialog(null, "Invalid input specified!", 
								"ImgurScraper - Error", 
								JOptionPane.ERROR_MESSAGE | JOptionPane.OK_OPTION);
						return;
					}
					logger.info("> %s", input);
					handleInput(input);
				}
			};
			this.console.onClose = new Delegate() {
				@Override
				public void call(Object... args) {
					System.exit(0);
				}
			};
			this.console.show();
		}
		
		this.printStartupMessage();
		while (this.running) {
			if (inputScanner != null) {
				System.out.print("> ");
				String input = inputScanner.nextLine().trim();
				if (input.length() < 1) {
					logger.error("Invalid input specified!");
					continue;
				}
				this.handleInput(input);
			}
		}
		
		if (inputScanner != null) {
			inputScanner.close();
		}
	}
	
	public static void main(String[] args) {
		new Thread(instance = new ImgurScraper(), "ImgurScraper-Main-Thread").start();
	}
}
