package me.vlod.imgurscraper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import me.vlod.imgurscraper.discord.webhook.DiscordEmbed;
import me.vlod.imgurscraper.discord.webhook.DiscordEmbedAuthor;
import me.vlod.imgurscraper.discord.webhook.DiscordEmbedFooter;
import me.vlod.imgurscraper.discord.webhook.DiscordWebhook;

public class CommandHandler {
	private ImgurScraper app;
	
	public CommandHandler(ImgurScraper app) {
		this.app = app;
	}
	
	public void doCommand(String cmd, String[] args) {
		switch (cmd) {
		case "generate":
			if (args.length < 1) {
				ImgurScraper.logger.error("Insufficient arguments!");
				break;
			}
			this.doGenerateCommand(args[0]);
			break;
		case "togglenosaving":
			this.doToggleNoSavingCommand();
			break;
		case "togglenodownload":
			this.doToggleNoDownloadCommand();
			break;
		case "setoutputfolderbase":
			if (args.length < 1) {
				ImgurScraper.logger.error("Insufficient arguments!");
				break;
			}
			this.doSetOutputFolderBaseCommand(args[0]);
			break;
		case "getlinkidformat":
			this.doGetLinkIDFormatCommand();
			break;
		case "setlinkidformat":
			if (args.length < 1) {
				ImgurScraper.logger.error("Insufficient arguments!");
				break;
			}
			this.doSetLinkIDFormatCommand(args[0]);
			break;
		case "setdiscordwebhook":
			if (args.length < 1) {
				ImgurScraper.logger.error("Insufficient arguments!");
				break;
			}
			this.doSetDiscordWebhookCommand(args[0]);
			break;
		case "clear":
			this.doClearCommand();
			break;
		case "exit":
			this.doExitCommand();
			break;
		case "help":
			this.doHelpCommand();
			break;
		default:
			ImgurScraper.logger.error("Invalid command! Type \"help\" for more information");
			break;
		}
	}
	
	private void doGenerateCommand(String numberRaw) {
		int number = 0;
		
		try {
			number = Integer.valueOf(numberRaw);
		} catch (Exception ex) {
			ImgurScraper.logger.error("Invalid arguments provided!");
			return;
		}
		
		long timeBeforeGen = System.currentTimeMillis();
		String outputFolderPath = "";
		
		if (this.app.noDownload) {
			ImgurScraper.logger.warn("Image downloading has been disabled!");
			ImgurScraper.logger.warn("The following features will not work:");
			ImgurScraper.logger.warn("- Discord webhook");
			ImgurScraper.logger.warn("- Properly checking for an unavailable image");
		}
		
		if (!this.app.noSaving) {
			outputFolderPath = (this.app.outputFolderBase == null ? 
					"." : this.app.outputFolderBase.trim()) + "/" + timeBeforeGen;
			ImgurScraper.logger.info("Output folder: %s", outputFolderPath);
			
			// Create the output folder if it doesn't exist
			try {
				File outputFolder = new File(outputFolderPath);
				if (!outputFolder.exists()) {
					outputFolder.mkdir();
				}	
			} catch (Exception ex) {
				ImgurScraper.logger.error("Unable to create the output folder!"
						+ " Do we not have write permissions?");
				ImgurScraper.logger.error("Disabled file saving");
				this.app.noSaving = true;
				ex.printStackTrace();
			}
		}

		String[] ids = this.app.generateIDs(number);
		PrintWriter fileDump = null;
		if (!this.app.noSaving) {
			try {
				fileDump = new PrintWriter(new FileOutputStream(
						new File(String.format("%s/links.html", outputFolderPath))));
				fileDump.println("<!Doctype HTML>");
				fileDump.println("<html>");
				fileDump.println("\t<body>");
				fileDump.println("\t\t<span>");
			} catch (Exception ex) {
				ImgurScraper.logger.error("Unable to create the HTML file!"
						+ " Do we not have write permissions?");
				ex.printStackTrace();
			}	
		}

		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			String link = String.format("https://imgur.com/%s", id);
			String linkImageURL = String.format("https://i.imgur.com/%s.png", id);
			boolean downloadResult = false;
			
			// Print to console
			ImgurScraper.logger.info("(%d) %s", i, link);
			
			if (!this.app.noSaving) {
				// Download image URL
				if (!this.app.noDownload) {
					downloadResult = this.app.downloadImage(linkImageURL, String.format("%s/%d.png", 
							outputFolderPath, i));
					
					if (!downloadResult) {
						linkImageURL = "";
					}
				}
				
				// Write to file
				if (fileDump != null) {
					fileDump.println(String.format(
							"\t\t\t(%d) <a href=\"%s\" target=_blank>%s</a>:<br>", i, link, link));
					fileDump.println(String.format(
							"\t\t\t<img style=\"color: red;\" src=\"%s\" alt=\"Link %d doesn\'t exist!\"/><br>", 
							linkImageURL, i));
				}
			}
			
			if (this.app.discordWebhook != null && downloadResult) {
				this.app.discordWebhook.setAvatarURL(
						"https://raw.githubusercontent.com/vlOd2/ImgurScraper/main/ImgurScraper.png");
				this.app.discordWebhook.addEmbed(new DiscordEmbed(
    					String.format("New scrape! (Number %d)", i), 
    					String.format("I have scraped the following link and confirmed it's valid: %s", link), 
    					5763719,
    					new DiscordEmbedFooter("Consider staring to show your support!"), 
    					null,
    					new DiscordEmbedAuthor("https://github.com/vlOd2/ImgurScraper")));
    			
				int webhookResponse = this.app.discordWebhook.submit();
				if (webhookResponse != 204) {
					ImgurScraper.logger.warn("Failed to send message to webhook!"
							+ " HTTP response code was %d", webhookResponse);
				}
			}
		}
		
		if (fileDump != null) {
			fileDump.println("\t\t</span>");
			fileDump.println("\t</body>");
			fileDump.println("</html>");
			fileDump.flush();
			fileDump.close();
		}
		
		long timeAfterGen = System.currentTimeMillis();
		ImgurScraper.logger.info("Generated" + (this.app.noSaving || this.app.noDownload ? " " : " & downloaded ") + 
				"%d links in %d seconds", ids.length, (timeAfterGen - timeBeforeGen) / 1000);
		
	}
	
	private void doToggleNoSavingCommand() {
		this.app.noSaving = !this.app.noSaving;
		ImgurScraper.logger.info("No saving is now %s", this.app.noSaving);
	}
	
	private void doToggleNoDownloadCommand() {
		this.app.noDownload = !this.app.noDownload;
		ImgurScraper.logger.info("No download is now %s", this.app.noDownload);
	}
	
	private void doSetOutputFolderBaseCommand(String path) {
		this.app.outputFolderBase = path;
		ImgurScraper.logger.info("Set output folder base to %s", path);
	}
	
	private void doGetLinkIDFormatCommand() {
		ImgurScraper.logger.info(this.app.linkIDFormat);
	}
	
	private void doSetLinkIDFormatCommand(String format) {
		this.app.linkIDFormat = format;
		ImgurScraper.logger.info("Set link ID format to %s", format);
	}
	
	private void doSetDiscordWebhookCommand(String webHook) {
		this.app.discordWebhook = webHook.length() < 1 ? null : new DiscordWebhook(webHook);
		ImgurScraper.logger.info("Set Discord webhook to %s", webHook);
	}
	
	private void doExitCommand() {
		this.app.running = false;
		
		if (this.app.console != null) {
			System.exit(0);
		}
	}
	
	private void doClearCommand() {
		for (int i = 0; i < 1000; i++) {
			System.out.println("");
		}
		
		if (this.app.console != null) {
			this.app.console.clear();
		}
		
		this.app.printStartupMessage();
	}
	
	private void doHelpCommand() {
		ImgurScraper.logger.info("generate <number> - Generates x amount of links");
		ImgurScraper.logger.info("setoutputfolderbase <path> - Sets the output folder base");
		ImgurScraper.logger.info("togglenosaving - Toggles no saving");
		ImgurScraper.logger.info("togglenodownload - Toggles no downloading");
		ImgurScraper.logger.info("getlinkidformat - Prints the link ID format used for generation");
		ImgurScraper.logger.info("setlinkidformat <format> - Sets the link ID format used for generation");
		ImgurScraper.logger.info("setdiscordwebhook <url> - Sets the Discord webhook URL to use"
				+ " (must have saving and downloading enabled)");
		ImgurScraper.logger.info("clear - Clears the screen");
		ImgurScraper.logger.info("exit - Exits ImgurScraper");
		ImgurScraper.logger.info("");
		ImgurScraper.logger.info("Link ID format information:");
		ImgurScraper.logger.info("c - random a-z small character");
		ImgurScraper.logger.info("C - random a-z big character");
		ImgurScraper.logger.info("i - random 0-9 number");
		ImgurScraper.logger.info("r - picks randomly between c, C and i");
	}
}
