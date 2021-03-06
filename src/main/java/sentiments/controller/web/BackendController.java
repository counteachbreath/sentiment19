package sentiments.controller.web;

import org.apache.commons.io.FileUtils;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sentiments.data.BasicDataImporter;
import sentiments.domain.model.DayStats;
import sentiments.domain.model.Language;
import sentiments.domain.model.query.Timeline;
import sentiments.domain.model.query.TweetFilter;
import sentiments.domain.repository.DayStatsRepository;
import sentiments.domain.repository.tweet.TweetRepository;
import sentiments.domain.service.LanguageService;
import sentiments.ml.service.ClassifierService;
import sentiments.ml.service.WordVectorBuilder;
import sentiments.ml.service.WordVectorsService;
import sentiments.service.ExceptionService;
import sentiments.service.StorageService;
import sentiments.service.TaskService;
import sentiments.domain.service.TimelineService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * @author Paw, 6runge
 */
@RestController
public class BackendController {

    private static final Logger log = LoggerFactory.getLogger(BackendController.class);


    @Autowired
    BasicDataImporter basicDataImporter;

    @Autowired
    ClassifierService classifierService;

    @Autowired
    TweetRepository tweetRepository;

    @Autowired
    LanguageService languageService;

    @Autowired
    TaskService taskService;

    @Autowired
    StorageService storageService;

    @Autowired
    ExceptionService exceptionService;

    @Autowired
    DayStatsRepository dayStatsRepository;

    @Autowired
    TimelineService timelineService;

    /**
     * The backend endpoint leads to a simple html page. It can be used to adjust some settings, upload data, and start
     * or stop tasks.
     */
    @RequestMapping("/backend")
    public ResponseEntity<String> backend(String message, HttpStatus status) {
        message = message == null ? "" : message;
        status = status == null ? HttpStatus.OK : status;
        String response = "";
        try {
            File file = ResourceUtils.getFile(
                    "classpath:frontend/sentiment-backend.html");
            response = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            response = response.replace("###MESSAGE###",message + System.lineSeparator()
                    + storageService.getReport() + System.lineSeparator()
                    + taskService.getLogContent());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            String eString = exceptionService.exceptionToString(e);
            log.warn(eString);
        } catch (IOException e) {
            e.printStackTrace();
            String eString = exceptionService.exceptionToString(e);
            log.warn(eString);
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");

        return new ResponseEntity<>(response, responseHeaders, status);
    }

    /**
     * Lets you set an existing directory as a target for file uploads via the backend.
     * @param dir the target directory. It needs to exist and be writable by the app.
     */
    @RequestMapping("/backend/setBaseDir")
    public ResponseEntity<String> setBaseDir(@RequestParam( value = "dir", defaultValue = "") String dir) {

        storageService.setStorageDir(dir);

        return backend("done.", HttpStatus.ACCEPTED); //new ResponseEntity<String>(response, responseHeaders,HttpStatus.CREATED);
    }

    /**
     * This endpoint can be used to enable or disable tasks (e.g. classify).
     * @param task the task of which the status will be set
     * @param enabled true to enable the task, false to disable it
     */
    @RequestMapping("/backend/setTaskStatus")
    public ResponseEntity<String> setTaskStatus(@RequestParam( value = "task", defaultValue = "") String task,
                                                           @RequestParam( value = "enabled", defaultValue = "false") boolean enabled) {
        if (task == "") {
            return backend("", HttpStatus.NOT_FOUND);
        }
        String response = "setting task '" + task  + "' to " + (enabled ? "active" : "not active");
        taskService.setTaskStatus(task, enabled);

        return backend(response, HttpStatus.ACCEPTED); //new ResponseEntity<String>(response, responseHeaders,HttpStatus.CREATED);
    }

    /**
     * Imports unlabelled tweets from the json-file specified in application.properties under the key localTweetJson.
     */
    @RequestMapping("/backend/import")
    public ResponseEntity<String> tweetimport() {

        this.basicDataImporter.importExampleJson();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");

        return new ResponseEntity<String>("finished", responseHeaders,HttpStatus.CREATED);
    }

    /**
     * This endpoint starts the import of test and training data for a given language. The data has to be located in the
     * tsv-files specified in the application.properties under the keys localTweetTsv.test & localTweetTsv.train.
     * @param lang the ISO-code of the language of the data to be imported
     */
    @RequestMapping("/backend/import/testandtrain")
    public ResponseEntity<String> testAndTrainimport(@RequestParam( value = "lang", defaultValue = "en") String lang) {
        System.out.println("testAndTrainimport was called with " + lang);
        this.basicDataImporter.importTsvTestAndTrain(languageService.getLanguage(lang));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");

        return new ResponseEntity<String>("finished", responseHeaders,HttpStatus.OK);
    }

    /**
     * This endpoint starts the import of test and training data for a given language. It ensures the correct ratio for
     * balanced training. The data has to be located in the tsv-files specified in the application.properties under the
     * keys localTweetTsv.test & localTweetTsv.train.
     * @param lang the ISO-code of the language of the data to be imported
     */
    @RequestMapping("/backend/import/traintwothirdsnonoff")
    public ResponseEntity<String> trainImportWithRatio(@RequestParam( value = "lang", defaultValue = "en") String lang) {
        System.out.println("testAndTrainimportWithRatio was called with " + lang);
        this.basicDataImporter.importFromTsvTwoThirdsOff(lang);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");

        return new ResponseEntity<String>("finished", responseHeaders,HttpStatus.OK);
    }

    /**
     * This endpoint starts the training of word vectors for the specified language.
     * @param lang the ISO-code of an active language
     */
    @RequestMapping("/backend/ml/w2vtraining")
    public ResponseEntity<String> w2vtraining(@RequestParam( value = "lang", defaultValue = "en") String lang) {
        WordVectorBuilder w2vb = new WordVectorBuilder(tweetRepository);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        try {
            Language language = languageService.getLanguage(lang);
            if (language == null) {
                return new ResponseEntity<String>("language not supported", responseHeaders,HttpStatus.NOT_FOUND);
            }
            w2vb.train(language);
            System.out.println("finished training");
        } catch (IOException e) {
            e.printStackTrace();
            String eString = exceptionService.exceptionToString(e);
            log.warn(eString);
            return new ResponseEntity<String>("Request failed", responseHeaders,HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>("finished training", responseHeaders,HttpStatus.CREATED);
    }

    /**
     * Tests the word vectors of a given language by printing a predefined set of words, each with its closest neighbors
     * in the vector space.
     * @param lang the ISO-code of an active language
     */
    @RequestMapping("/backend/ml/w2vtest")
    public ResponseEntity<String> w2vtest(@RequestParam( value = "lang", defaultValue = "en") String lang) {

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        Language language = languageService.getLanguage(lang);
        if (language == null) {
            return new ResponseEntity<String>("language not supported", responseHeaders,HttpStatus.NOT_FOUND);
        }

        WordVectors word2VecModel = WordVectorsService.getWordVectors(language);

        String examples = "Some words with their closest neighbours: \n";

        Collection<String> list = word2VecModel.wordsNearest("woman" , 10);
        examples += " woman: " + list + ",  ";

        list = word2VecModel.wordsNearest("man" , 10);
        examples += " man: " + list + ",  ";

        list = word2VecModel.wordsNearest("girl" , 10);
        examples += " girl: " + list + ",  ";

        list = word2VecModel.wordsNearest("boy" , 10);
        examples += " boy: " + list + ",  ";

        list = word2VecModel.wordsNearest("day" , 10);
        examples += " day: " + list + ",  ";

        list = word2VecModel.wordsNearest("night" , 10);
        examples += " night: " + list + ",  ";

        list = word2VecModel.wordsNearest("shit" , 10);
        examples += " shit: " + list + ",  ";

        list = word2VecModel.wordsNearest("motherfucker" , 10);
        examples += " motherfucker: " + list + ",  ";

        list = word2VecModel.wordsNearest("cat" , 10);
        examples += " cat: " + list + ",  ";

        list = word2VecModel.wordsNearest("merkel" , 10);
        examples += " merkel: " + list + ",  ";

        list = word2VecModel.wordsNearest("trump" , 10);
        examples += " trump: " + list + ",  ";

        list = word2VecModel.wordsNearest("germany", 10);
        examples += " germany: " + list + ",  ";

        list = word2VecModel.wordsNearest("usa", 10);
        examples += " usa: " + list + ",  ";

        list = word2VecModel.wordsNearest("nobody", 10);
        examples += " nobody: " + list + " ";

        return new ResponseEntity<String>(examples, responseHeaders,HttpStatus.OK);
    }

    /**
     * Starts the training of a classifier for the specified language.
     * @return the ISO-code of an active language
     */
    @RequestMapping("/backend/ml/trainnet")
    public ResponseEntity<String> trainNet(@RequestParam( value = "lang", defaultValue = "en") String lang) {
        HttpHeaders responseHeaders = new HttpHeaders();
        try {
            Language language = languageService.getLanguage(lang);
            if (language == null) {
                return new ResponseEntity<>("language not supported", responseHeaders, HttpStatus.NOT_FOUND);
            }
            classifierService.trainClassifier(language);
        } catch (Exception e) {
            String eString = exceptionService.exceptionToString(e);
            log.warn(eString);
            return new ResponseEntity<String>("Request failed", responseHeaders,HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>("training done", responseHeaders,HttpStatus.CREATED);
    }

    /**
     * Uploads a file to the working directory or, if one has been set, the directory specified with {@link BackendController#setBaseDir(String)}
     * @param file the file to be uploaded
     */
    @PostMapping("/backend/upload")
    public ResponseEntity<String> singleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return  backend("No File found. Please select a file to upload.", HttpStatus.BAD_REQUEST);
        }
        try {
            File target = storageService.getFile(file.getOriginalFilename());
            target.setWritable(true);
            FileUtils.copyInputStreamToFile(file.getInputStream(), target);
        } catch (IOException e) {
            e.printStackTrace();
            String eString = exceptionService.exceptionToString(e);
            log.warn("Exception during Fileupload: " + eString);
            return backend("Woahh... it ain't all good. INTERNAL ERROR." + eString, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return backend("You successfully uploaded '" + file.getOriginalFilename() + "'", HttpStatus.CREATED);
    }

    /**
     * This endpoint is used in our html-backend to import data for training and testing.
     * The ratio has to be 1:2 (offensive:non-offensive)
     * @param trainData a file with the training data
     * @param testData a file with the test data
     * @param lang the ISO-code of the language for which  data is to be imported
     */
    @PostMapping("/backend/import/training")
    public ResponseEntity<String> testAndTrainImport(@RequestParam("traindata") MultipartFile trainData,
                                                     @RequestParam("testdata") MultipartFile testData,
                                                     @RequestParam( value = "lang", defaultValue = "") String lang) {
        Language language = languageService.getLanguage(lang);
        if (language == null) {
            backend("language not supported", HttpStatus.NOT_FOUND);
        }
        MultipartFile[] files = {trainData, testData};
        List<File> targetFiles = new LinkedList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                targetFiles.add(null);
                break;
            }
            try {
                File target = storageService.getFile("training/upload/" + file.getOriginalFilename());
                target.setWritable(true);
                FileUtils.copyInputStreamToFile(file.getInputStream(), target);
                targetFiles.add(target);
            } catch (IOException e) {
                e.printStackTrace();
                String eString = exceptionService.exceptionToString(e);
                log.warn(eString);
                log.warn("Exception during Fileupload: " + eString);

                return backend("Woahh... it ain't all good. INTERNAL ERROR." + eString, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        basicDataImporter.importTsvTestAndTrain(language, targetFiles.get(0).getPath(), targetFiles.get(1).getPath());
        return backend("success", HttpStatus.OK);
    }

    /**
     * Generates the timeline for the whole available timeframe and persists it in the database ignoring hashtags
     */
    @RequestMapping("/backend/createdaystats")
    public ResponseEntity<String> createDayStats() {
        for (Language language : languageService.getAvailableLanguages()) {
            TweetFilter.Builder tweetFilterBuilder = new TweetFilter.Builder();
            List<String> langList = new ArrayList<>();
            langList.add(language.getIso());
            Timestamp start = Timestamp.valueOf(tweetRepository.getFirstDate().atTime(LocalTime.MIDNIGHT));
            Timestamp end = Timestamp.valueOf(tweetRepository.getLastDate().atTime(LocalTime.MIDNIGHT));
            Timeline offensiveTimeline = tweetRepository.countByOffensiveAndDayInInterval(tweetFilterBuilder.setStart(start).setEnd(end).setLanguages(langList).setOffensive(true).build());
            Timeline nonoffensiveTimeline = tweetRepository.countByOffensiveAndDayInInterval(tweetFilterBuilder.setStart(start).setEnd(end).setLanguages(langList).setOffensive(false).build());
            LocalDate current = offensiveTimeline.start;
            Iterator<Integer> nonoffensiveIterator = nonoffensiveTimeline.timeline.iterator();
            Iterator<Integer> offensiveIterator = offensiveTimeline.timeline.iterator();
            while (offensiveIterator.hasNext()) {
                DayStats dayStats = new DayStats();
                dayStats.setDate(current);
                dayStats.setLanguage(language.getIso());
                dayStats.setNonoffensive(nonoffensiveIterator.next());
                dayStats.setOffensive(offensiveIterator.next());
                current = current.plusDays(1);
                dayStatsRepository.save(dayStats);
            }
        }
            return backend("done!", HttpStatus.CREATED);
    }

    /**
     * Changes the way the data for the timeline is generated.
     * @param version either 0 or 1
     */
    @RequestMapping("/backend/settimelineversion")
    public ResponseEntity<String> setTimelineVersion(@RequestParam("version") int version) {
        timelineService.setVersion(version);
        return backend("version set to " + version, HttpStatus.OK);
    }
}
