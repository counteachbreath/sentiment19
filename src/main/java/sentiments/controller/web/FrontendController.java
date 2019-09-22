package sentiments.controller.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;
import sentiments.domain.model.HashtagCount;
import sentiments.domain.model.Timeline;
import sentiments.domain.model.TweetFilter;
import sentiments.domain.repository.TweetRepository;
import sentiments.domain.service.LanguageService;
import sentiments.domain.service.ResponseService;
import sentiments.ml.W2VTweetClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class FrontendController extends BasicWebController {

    @Autowired
    Environment env;

    @Autowired
    W2VTweetClassifier tweetClassifier;

    @Autowired
    TweetRepository tweetRepository;

    @Autowired
    LanguageService languageService;

    @Autowired
    ResponseService responseService;

    @RequestMapping("/")
    public ResponseEntity<String> html() {
        String response = "";
        try {
            File file = ResourceUtils.getFile(
                    "classpath:frontend/sentiment-frontend.html");
            response = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");

        return new ResponseEntity<String>(response, responseHeaders,HttpStatus.CREATED);
    }

    @RequestMapping(value = "/tweet",  method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<String> tweet(@RequestBody TweetFilter tf) {
        String base_url = "https://publish.twitter.com/oembed?url=https://twitter.com/user/status/";
        String twitterId;
        JsonObject obj = null;

        int responseCode = 0;
        int i = 0;
        while (responseCode != 200 && i < 100) {
            i++;
            try {
                twitterId = tweetRepository.getRandomTwitterId(tf);
                if (twitterId == null) break;
                String url = base_url + twitterId + "&align=center";
                URL urlObj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();

                // optional default is GET
                con.setRequestMethod("GET");

                //add request header
                responseCode = con.getResponseCode();
                System.out.println("\nSending 'GET' request to URL : " + url);
                System.out.println("Response Code : " + responseCode);

                JsonReader reader = new JsonReader(new InputStreamReader(con.getInputStream()));
                obj = new JsonParser().parse(reader).getAsJsonObject();
                reader.close();

            } catch (IOException e) {

            }
        }
        String str = null;
        if (obj != null) {
            str = obj.get("html").getAsString();
        } else {
            str = "<h3>Couldn't fetch tweet</h3>";
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        JSONObject out = new JSONObject();
        out.put("html", str);
        return new ResponseEntity<String>(out.toString(), responseHeaders, HttpStatus.CREATED);
    }

    @RequestMapping("/sentiments")
    public ResponseEntity<String> home(@RequestParam(value = "tweet", defaultValue = "") String tweet, @RequestParam(value = "format", defaultValue = "text") String format) {
        String cleanTweet = tweet.replace("\r", " ").replace("\n", " ").trim();
        System.out.println("tweet:" + cleanTweet);
        String cleanFormat = format.replace("\r", " ").replace("\n", " ").trim();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        String classification = tweetClassifier.classifyTweet(cleanTweet, languageService.getLanguage("en"));
        String response;
        if (cleanFormat.compareTo("json") == 0) {
            response = responseService.generateJSONResponse(classification);
        } else {
            response = responseService.generateTextResponse(classification);
        }

        return new ResponseEntity<String>(response, responseHeaders,HttpStatus.CREATED);
    }

    @RequestMapping(value = "/stats",  method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<String> stats(@RequestBody TweetFilter tf) {

        int count = tweetRepository.countByOffensiveAndDate(tf);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        JSONObject out = new JSONObject();
        out.put("count", count);
        return new ResponseEntity<String>(out.toString(), responseHeaders,HttpStatus.CREATED);
    }

    @RequestMapping(value = "/popularhashtags",  method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<String> popularhashtags(@RequestBody TweetFilter tf, @RequestParam( value = "limit", defaultValue = "5") int limit ) {

        List<HashtagCount> tags = tweetRepository.getMostPopularHashtags(tf, limit);
        int total = tweetRepository.countByOffensiveAndDate(tf);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        JSONArray hashtags = new JSONArray();
        hashtags.addAll(tags.stream().map(HashtagCount::toJSONObject).collect(Collectors.toList()));
        JSONObject out = new JSONObject();
        out.put("hashtags", hashtags );
        out.put("total", total );
        return new ResponseEntity<String>(out.toString(), responseHeaders,HttpStatus.CREATED);
    }

    @RequestMapping(value="/timeline", method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<String> timeline(@RequestBody TweetFilter tf) {

        Timeline timeline = tweetRepository.countByOffensiveAndDayInInterval(tf);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        JSONObject out = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.addAll(timeline.timeline);
        out.put("timeline", arr);
        out.put("start", timeline.start.toString());
        out.put("end", timeline.end.toString());
        return new ResponseEntity<String>(out.toString(), responseHeaders,HttpStatus.CREATED);
    }

}
