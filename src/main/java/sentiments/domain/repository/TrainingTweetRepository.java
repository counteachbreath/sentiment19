package sentiments.domain.repository;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import sentiments.domain.model.Tweet;

public interface TrainingTweetRepository extends MongoRepository<Tweet,Integer> {

	//return an Iterable wit all (non-) offensive tweets
	@Query("{ 'offensive' : ?0 }")
	public Iterable<Tweet> findAllByOffensive(Boolean offensive);

	//returns an Iterable of all tweets that are labled as test/training and (non-) offensive
	@Query("{ 'test' : ?1 , 'offensive' : ?0 }")
	public Iterable<Tweet> findAllByTestAndOffensive(Boolean test, Boolean offensive);

	//returns the count of all samples
	@Query(value="{}", count = true)
	public int count(boolean test);

	//returns the count of test/training samples
	@Query(value="{ 'test' : ?0 }", count = true)
	public int countTest(boolean test);
}
