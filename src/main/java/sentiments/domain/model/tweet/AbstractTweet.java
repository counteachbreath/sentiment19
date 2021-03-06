package sentiments.domain.model.tweet;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Paw
 */
@MappedSuperclass
public class AbstractTweet {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Integer uid;
	@Lob
	private String text;

	private String language;

	private Date crdate;

	private String day;

	private String month;

	private String year;

	private Date tmstamp;
	private boolean offensive;

	public AbstractTweet() {
	}

	public Integer getUid() {
		return uid;
	}

	public void setUid(Integer uid) {
		this.uid = uid;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}


	public Date getCrdate() {
		return crdate;
	}

	public void setCrdate(Date crdate) {
		this.crdate = crdate;
	}

	public Date getTmstamp() {
		return tmstamp;
	}

	public void setTmstamp(Date tmstamp) {
		this.tmstamp = tmstamp;
	}

	public boolean isOffensive() {
		return offensive;
	}

	public void setOffensive(boolean offensive) {
		this.offensive = offensive;
	}

	public String getDay() {
		return day;
	}

	public void setDay(String day) {
		this.day = day;
	}

	public String getMonth() {
		return month;
	}

	public void setMonth(String month) {
		this.month = month;
	}

	public String getYear() {
		return year;
	}

	public void setYear(String year) {
		this.year = year;
	}
}