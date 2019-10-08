package sentiments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sentiments.data.ImportManager;
import sentiments.domain.model.Language;
import sentiments.domain.model.tweet.Tweet;
import sentiments.domain.repository.tweet.TweetRepository;
import sentiments.domain.service.LanguageService;
import sentiments.ml.classifier.Classifier;
import sentiments.ml.service.ClassifierService;
import sentiments.service.ExceptionService;
import sentiments.service.TaskService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * @author Paw , 6runge
 */
@Component
public class ScheduledTasks {

    @Autowired
    private ImportManager importManager;

    @Autowired
    private ClassifierService classifierService;

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ExceptionService exceptionService;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    private static int maxThreadCount = 4;

    private static int threadCount = 0;

    private static boolean classifying = false;

   // private static int batchSize = 1048;

    @Scheduled(cron = "*/5 * * * * *")
    public void classifyNextBatch() {
        boolean execute = taskService.checkTaskExecution("classify");
        if (!execute || classifying) {
            return;
        }
            classifying = true;
            Iterable<Language> langs = languageService.getAvailableLanguages();

        long time = System.currentTimeMillis();
        AtomicInteger tweetCount = new AtomicInteger();

        for (Language lang : langs) {
                log.info("try classifying " + lang.getIso() + " tweets");

                Classifier classifier = classifierService.getClassifier(lang);
                if (classifier == null) {
                continue;
                }
                Date runDate = new Date();

            while (true) {
                    Stream<Tweet> tweets = tweetRepository.find100kByClassifiedAndLanguage(null, lang.getIso());
                    AtomicInteger index = new AtomicInteger(0);
                    int batchSize = 4096;
                    int multiBatch = 1;
                    Stream<List<Tweet>> stream = tweets.collect(Collectors.groupingBy(x -> index.getAndIncrement() / batchSize ))
                            .entrySet().stream()
                            .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue);
                    // remove .parallel() for serial
                    AtomicInteger classified = new AtomicInteger(0);
                    stream.forEach(tweetList -> {
                        tweetCount.addAndGet(tweetList.size());
                        classified.addAndGet(1);
//                        classifier.classifyTweets(tweetList,runDate);
//                        tweetRepository.saveAll(tweetList);

                        // --- bulkOps  multiBatch just multiplies batchSize to get effective batchSize
                    BulkOperations ops = tweetRepository.getBulkOps();
                    classifier.classifyTweets(tweetList,runDate);
                    for (Tweet tweet : tweetList) {
                        Update update = new Update();
                        update.set("offensive", tweet.isOffensive());
                        update.set("classified", runDate);
                        ops.updateOne(query(where("_id").is(tweet.get_id())), update);
                    }
                    ops.execute();

                        // single batch multiBatch needs to be set to one
//                    for(Tweet tweet: tweetList) {
//                        Classification classification = classifier.classifyTweet(tweet.getText());
//                        tweet.setOffensive(classification.isOffensive());
//                        tweet.setClassified(runDate);
//                        batch.add(tweet);
//                        if (batch.size() % batchSize == 0) {
//                            tweetRepository.saveAll(batch);
//                            batch.clear();
//                        }
//                    }
//                    tweetRepository.saveAll(tweetList);

                        // multi batch need to set multiBatch > 1
//                    List<Tweet> batch = new LinkedList<>();
//                    for(Tweet tweet: tweetList) {
//                        Classification classification = classifier.classifyTweet(tweet.getText());
//                        tweet.setOffensive(classification.isOffensive());
//                        tweet.setClassified(runDate);
//                        batch.add(tweet);
//                        if (batch.size() % batchSize == 0) {
//                            tweetRepository.saveAll(batch);
//                            batch.clear();
//                        }
//                    }

                    });
                long timeOverall = (System.currentTimeMillis() - time);
                String report;
                report = "##CLASSIFYING## Overall Time: " + timeOverall + "ms" + System.lineSeparator();
                report += "##CLASSIFYING## ~ " + tweetCount.get() * 1000 / timeOverall + " tweets per sec" + System.lineSeparator();
                report += "##CLASSIFYING## Tried to classify " + tweetCount.get() + " tweets. Done.";
                log.info(report);
                taskService.log(report);
                classifying = false;
                    stream.close();
                    //System.gc();
                    if (classified.get() <= 1) break;
                }

            }



    }


    @Async
    @Scheduled(cron = "*/5 * * * * *")
    public void crawlDataServer() throws InterruptedException {
        boolean execute = taskService.checkTaskExecution("import");
        if (execute && threadCount < maxThreadCount) {
            int mycount = ++threadCount;
            log.info("Starting crawl (" + mycount + ") at {}", dateFormat.format(new Date()));
            taskService.log("Starting crawl (" + mycount + ") at " + dateFormat.format(new Date()));
            CompletableFuture completableFuture = importManager.importTweets();
            try {
                System.out.println(completableFuture.get());
            } catch (ExecutionException e) {

                e.printStackTrace();

                String exceptionAsString = exceptionService.exceptionToString(e);

                log.warn("Crawl Exception: " + exceptionAsString);
                taskService.log("Crawl Exception: " + exceptionAsString);

            }
            log.info("Ending crawl (" + mycount + ")  at {}", dateFormat.format(new Date()));
            taskService.log("Starting crawl (" + mycount + ") at " + dateFormat.format(new Date()));

            threadCount--;
        }
    }
}