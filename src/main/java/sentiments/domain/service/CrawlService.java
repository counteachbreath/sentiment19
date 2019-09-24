package sentiments.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sentiments.domain.model.Crawl;
import sentiments.domain.repository.CrawlRepository;

import java.time.LocalDateTime;
import java.time.Month;

@Service
public class CrawlService {

    @Autowired
    private CrawlRepository crawlRepository;

    public LocalDateTime getLastStart() {
        Crawl crawl = crawlRepository.findTopByOrderByDateDesc();
        System.out.println(crawl.getDate());
        if (crawl == null) {
            return LocalDateTime.of(2018, Month.JANUARY,1, 0, 0);
        }
        return crawl.getDate();
    }

    public void newCrawl(LocalDateTime date) {
        Crawl crawl = new Crawl();
        crawl.setDate(date);
        crawl.setStatus(Crawl.IN_PROGRESS);
        crawlRepository.save(crawl);
    }

    public void finishCrawl(LocalDateTime date) {
        Crawl crawl = crawlRepository.findByDate(date);
        crawl.setStatus(Crawl.FINISHED);
        crawlRepository.save(crawl);
    }

}
