package sentiments.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sentiments.domain.model.AbstractTweet;
import sentiments.domain.model.TrainingTweet;
import sentiments.domain.model.Tweet;
import sentiments.domain.preprocessor.ImportTweetPreProcessor;
import sentiments.domain.preprocessor.TweetPreProcessor;
import sentiments.domain.repository.TrainingTweetRepository;
import sentiments.domain.repository.TweetRepository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;



/**
 * @author Paw
 *
 */
@Transactional
@Service
public class BasicDataImporter {

	private static final int BATCH_SIZE = 1024;

	@Autowired
	Environment env;

	@Autowired
	TweetRepository tweetRepository;

	@Autowired
	TrainingTweetRepository trainingTweetRepository;

	DateTimeFormatter dateTimeFormatter;

	public BasicDataImporter() {
		super();
		this.dateTimeFormatter = DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss Z yyyy", Locale.UK);
	}

	public void importFromJson(String jsonPath, TweetProvider<Tweet> tweetProvider, TweetPreProcessor processor, MongoRepository repo)
	{
		try {
			InputStream stream = new FileInputStream(jsonPath);
			importFromStream(stream, tweetProvider, processor, repo);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void importFromStream(InputStream stream, TweetProvider<Tweet> tweetProvider, TweetPreProcessor processor, MongoRepository repo) {
		try {
			JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			Gson gson = new GsonBuilder().create();
			reader.setLenient(true);
			List<Tweet> tweets = tweetProvider.getNewTweetList();
			int i = 0;
			JsonElement element;
			JsonObject object;
			Tweet tweet;
			while (reader.hasNext()) {
				// Read data into object model

				try {
					if (reader.peek() == JsonToken.END_DOCUMENT) {
						break;
					}
					element = gson.fromJson(reader, JsonElement.class);
					object = element.getAsJsonObject();
					tweet = tweetProvider.createTweet();
					this.mapJsonToTweet(object, tweet);
					if (tweet != null && tweet.getText() != null) {
						i++;
						processor.preProcess(tweet);
						tweets.add(tweet);
					}

				} catch (IllegalStateException | JsonSyntaxException e) {
					reader.skipValue();
				}
				// persist tweets in batch
				if (i % BATCH_SIZE == 0) {
					System.out.println(i);
					tweetRepository.saveAll(tweets);
					tweets.clear();
				}
			}
			tweetRepository.saveAll(tweets);
			tweets.clear();
			reader.close();
			processor.destroy();
			System.gc();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void importFromStream(InputStream stream) {
		importFromStream(stream, new TweetProvider<Tweet>() {
					@Override
					public Tweet createTweet() {
						return new Tweet();
					}
				}, new ImportTweetPreProcessor()
				, tweetRepository);
	}

	public void importFromTsv(String tsvPath, TweetProvider<TrainingTweet> tweetProvider, MongoRepository repo, TweetPreProcessor processor) {
		Reader in;
		int i = 0;
		try {
			FileInputStream fstream = new FileInputStream(tsvPath);
			in = new BufferedReader(new InputStreamReader(fstream));
			Iterable<CSVRecord> records = CSVFormat.TDF.withHeader().parse(in);
			List<TrainingTweet> tweets = tweetProvider.getNewTweetList();
			for (CSVRecord record : records) {
				TrainingTweet tweet = tweetProvider.createTweet();
				this.mapTsvToTweet(record, tweet);
				if (tweet != null && tweet.getText() != null) {
					i++;
					processor.preProcess(tweet);
					tweets.add(tweet);
				}
				System.out.println(i);
				if (i % BATCH_SIZE == 0) {
					repo.saveAll(tweets);
					tweets.clear();
				}
			}
			if (!tweets.isEmpty()) {
				repo.saveAll(tweets);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// persist tweets in batch (256 per insert)
//		entityManager.flush();
//		entityManager.clear();
	}

	private void mapTsvToTweet(CSVRecord record, AbstractTweet tweet) {
		tweet.setText(record.get("tweet"));
		switch (record.get("subtask_a")) {
			case "OFF":
				tweet.setOffensive(true);
				break;
			case "NOT":
				tweet.setOffensive(false);
				break;
			default:
		}
	}

	private void mapJsonToTweet(JsonObject object, Tweet tweet) {
		String text = null;
		if (object.has("full_text")) {
			text = object.get("full_text").getAsString();
		} else if (object.has("text")){
			text = object.get("text").getAsString();
		}
		tweet.setText(text);
		if (object.has("id_str")) {
			tweet.setTwitterId(object.get("id_str").getAsString());
		}

		if (object.has("created_at")) {
			LocalDateTime dateTime;
			try {
				dateTime = LocalDateTime.parse(object.get("created_at").getAsString(), this.dateTimeFormatter);
				tweet.setCrdate(Timestamp.valueOf(dateTime));
			} catch (DateTimeParseException | NullPointerException e) {
				Parser parser = new Parser();
				List<DateGroup> groups = parser.parse(object.get("created_at").getAsString());

				// Get Natty's interpreted Date
				Instant targetDate = groups.get(0).getDates().get(0).toInstant();
				tweet.setCrdate(Timestamp.from(targetDate));
			}
		}
		tweet.setTmstamp(Timestamp.valueOf(LocalDateTime.now()));
	}

	public void importExampleJson() {
		String jsonPath = this.env.getProperty("localTweetJson");
		importFromJson(jsonPath, new TweetProvider<Tweet>() {
					@Override
					public Tweet createTweet() {
						return new Tweet();
					}
				}, new ImportTweetPreProcessor()
				, tweetRepository);
	}

	public void importTsvTestAndTrain() {
		TweetPreProcessor preproc = new ImportTweetPreProcessor();
		importFromTsv(this.env.getProperty("localTweetTsv.train"), new TweetProvider<TrainingTweet>() {
			@Override
			public TrainingTweet createTweet() {
				return new TrainingTweet();
			}
		}, trainingTweetRepository, preproc);
		importFromTsv(this.env.getProperty("localTweetTsv.test"),new TweetProvider<TrainingTweet>()
		{
			@Override
			public TrainingTweet createTweet() {
				TrainingTweet tweet = new TrainingTweet();
				tweet.setTest(true);
				return tweet;
			}

		}, trainingTweetRepository, preproc);
	}
}

