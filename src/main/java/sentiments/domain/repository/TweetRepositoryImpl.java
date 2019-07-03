package sentiments.domain.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.SampleOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import sentiments.domain.model.DayCount;
import sentiments.domain.model.Tweet;
import sentiments.domain.model.TweetQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class TweetRepositoryImpl implements TweetRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public TweetRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Integer> countByOffensiveAndDayInInterval(TweetQuery tweetQuery) {
        List<AggregationOperation> list = getWhereOperations(tweetQuery);
        list.add(Aggregation.project()
                .andExpression("year(crdate)").as("year")
                .andExpression("month(crdate)").as("month")
                .andExpression("dayOfMonth(crdate)").as("day1"));
        list.add(Aggregation.group(Aggregation.fields().and("year").and("month").and("day1"))
                .count().as("count"));
        list.add(Aggregation.sort(Sort.Direction.ASC, "year", "month", "day1"));
        list.add(Aggregation.project("count").andExpression("concat(substr(year,0,-1),'-',substr(month,0,-1),'-',substr(day1,0,-1))").as("day"));


        Aggregation agg = Aggregation.newAggregation(list);


        List<Integer> result = new LinkedList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.US);
        LocalDate current = tweetQuery.getStart().toLocalDateTime().toLocalDate();

        for (DayCount i : mongoTemplate.aggregate(agg, Tweet.class, DayCount.class)) {
            LocalDate currentDate = LocalDate.parse(i.day, f);
            while (current.compareTo(currentDate) < 0) {
                result.add(0);
                current = current.plusDays(1);
            }
            current = current.plusDays(1);
            result.add(i.count);
        }
        while (current.compareTo(tweetQuery.getEnd().toLocalDateTime().toLocalDate()) < 0) {
            result.add(0);
            current = current.plusDays(1);
        }

        return result;
    }

    @Override
    public String getRandomTwitterId(TweetQuery tweetQuery) {
        SampleOperation sampleStage = Aggregation.sample(1);
        List<AggregationOperation> list = getWhereOperations(tweetQuery);
        list.add(sampleStage);
        list.add(Aggregation.project("twitterId").andExclude("_id"));
        Aggregation aggregation = Aggregation.newAggregation(list);
        AggregationResults<Tweet> output = mongoTemplate.aggregate(aggregation, Tweet.class, Tweet.class);
        List<Tweet> mappedOutput = output.getMappedResults();
        String result = (mappedOutput.size() > 0) ? mappedOutput.get(0).getTwitterId() : null;
        return result;
    }

    private List<AggregationOperation> getWhereOperations(TweetQuery tweetQuery) {
        List<AggregationOperation> list = new LinkedList<>();

        //offensive?
        //TODO what if null?
        list.add(Aggregation.match(Criteria.where("offensive").is(tweetQuery.isOffensive())));
        //timeframe
        Criteria c = Criteria.where("crdate");
        LocalDateTime start = LocalDateTime.ofInstant(tweetQuery.getStart().toInstant(), ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.ofInstant(tweetQuery.getEnd().toInstant(), ZoneId.of("UTC")).withHour(23).withMinute(59).withSecond(59);
        boolean addTimeQuery = false;
        if (tweetQuery.getStart() != null) {
            c = c.gte(start.toInstant(ZoneOffset.UTC));
            addTimeQuery = true;
        }
        if (tweetQuery.getEnd() != null) {
            c = c.lte(end.toInstant(ZoneOffset.UTC));
            addTimeQuery = true;
        }
        if (addTimeQuery) {
            list.add(Aggregation.match(c));
        }
        //hashtags
        if (tweetQuery.getHashtags() != null) {
            list.add(Aggregation.match(Criteria.where("hashtags").all(tweetQuery.getHashtags())));
        }

        //hashtags
        if (tweetQuery.getLanguages() != null) {
            list.add(Aggregation.match(Criteria.where("language").in(tweetQuery.getLanguages())));
        }

        return list;
    }

}
