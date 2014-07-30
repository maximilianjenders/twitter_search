package org.twittersearch.app.twitter_api_usage;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import twitter4j.HashtagEntity;
import twitter4j.Status;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Mandy Roick on 14.07.2014. according to example by Maximilian Jenders
 */
public class DBManager {
    private Connection connection;

    private String password = "";
    private String user = "";

    public DBManager() {
        connect();
    }

    public void connect() {
        try {
            connection = DriverManager.getConnection(
                            "jdbc:postgresql://isfet.hpi.uni-potsdam.de:5432/max?searchpath=mandy_masterarbeit",
                            this.user, this.password);
        } catch (SQLException e) {
            System.out.println("Could not connect to PostgreSQL-Database.");
            e.printStackTrace();
        }
    }

    protected void finalize() throws Throwable {
        this.connection.close();
        super.finalize();
    }

    public void writeTweetToDB(Status tweet) {
        if (tweetDoesExist(tweet.getId())) {
            updateTweet(tweet);
        } else {
            createTweet(tweet);
        }
    }

    private boolean tweetDoesExist(long tweetId) {
        boolean doesExist = false;
        try {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM mandy_masterarbeit.twitter_tweet WHERE id = " + tweetId);

            if(result.next()) {
                doesExist = true;
            }

            statement.close();
        } catch (SQLException e) {
            System.out.println("Could not test whether Tweet exists in DB!");
            e.printStackTrace();
        }

        return doesExist;
    }

    private void updateTweet(Status tweet) {
        try {
            PreparedStatement statement = connection.prepareStatement("" +
                    "UPDATE mandy_masterarbeit.twitter_tweet " +
                    "SET content = ?, " +
                    "user_id = ?, " +
                    "created_at = ?, " +
                    "reply_id = ?, " +
                    "language = ?, " +
                    "retweeted = ?, " +
                    "retweeted_id = ?, " +
                    "truncated = ?, " +
                    "coordinates_x = ?, coordinates_y = ?, " +
                    "place_id = ? " +
                    "WHERE id = ?");

            statement.setString(1, tweet.getText());
            statement.setLong(2, tweet.getUser().getId());
            java.sql.Date sqlDate = new Date(tweet.getCreatedAt().getTime());
            statement.setDate(3, sqlDate);
            statement.setLong(4, tweet.getInReplyToStatusId());
            statement.setString(5, tweet.getLang());

            statement.setBoolean(6, tweet.isRetweet());
            if (tweet.isRetweet()) {
                statement.setLong(7, tweet.getRetweetedStatus().getId());
            } else {
                statement.setNull(7, Types.BIGINT);
            }
            statement.setBoolean(8, tweet.isTruncated());
            if (tweet.getGeoLocation() != null) {
                statement.setDouble(9, tweet.getGeoLocation().getLongitude());
                statement.setDouble(10, tweet.getGeoLocation().getLatitude());
            } else {
                statement.setNull(9, Types.DOUBLE);
                statement.setNull(10, Types.DOUBLE);
            }

            if (tweet.getPlace() == null) {
                statement.setNull(11, Types.VARCHAR);
            } else {
                statement.setString(11, tweet.getPlace().getId());
            }


            statement.setLong(12, tweet.getId());

            statement.execute();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTweet(Status tweet) {
        try {
            PreparedStatement statement = connection.prepareStatement("" +
                    "INSERT INTO mandy_masterarbeit.twitter_tweet(id, content, user_id, created_at, " +
                    "reply_id, language, retweeted, retweeted_id, truncated, place_id, coordinates_x, coordinates_y)" +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? , ?)");

            statement.setLong(1, tweet.getId());
            statement.setString(2, tweet.getText());
            statement.setLong(3, tweet.getUser().getId());
            java.sql.Date sqlDate = new Date(tweet.getCreatedAt().getTime());
            statement.setDate(4, sqlDate);
            statement.setLong(5, tweet.getInReplyToStatusId());
            statement.setString(6, tweet.getLang());

            statement.setBoolean(7, tweet.isRetweet());
            if (tweet.isRetweet()) {
                statement.setLong(8, tweet.getRetweetedStatus().getId());
            } else {
                statement.setNull(8, Types.BIGINT);
            }
            statement.setBoolean(9, tweet.isTruncated());
            if (tweet.getPlace() == null) {
                statement.setNull(10, Types.VARCHAR);
            } else {
                statement.setString(10, tweet.getPlace().getId());
            }
            if (tweet.getGeoLocation() != null) {
                statement.setDouble(11, tweet.getGeoLocation().getLongitude());
                statement.setDouble(12, tweet.getGeoLocation().getLatitude());
            } else {
                statement.setNull(11, Types.DOUBLE);
                statement.setNull(12, Types.DOUBLE);
            }

            statement.execute();
            statement.close();
        } catch (SQLException e) {
            System.out.println("Tweet could not be saved to DB for Tweet " + tweet.getText());
            e.printStackTrace();
        }
    }

    //---------------------------------    Hashtags    ------------------------------------//

    public void writeHashtagsToDB(Status tweet) {
        HashtagEntity[] hashtags = tweet.getHashtagEntities();
        for (HashtagEntity hashtag : hashtags) {
            if (!hashtagForTweetDoesExist(tweet.getId(), hashtag.getText())) {
                createHashtagForTweet(tweet, hashtag);
            }
        }
    }

    private boolean hashtagForTweetDoesExist(long tweetId, String hashtagText) {
        boolean doesExist = false;
        try {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM mandy_masterarbeit.twitter_tweet_hashtag WHERE (hashtag = '" +
                    hashtagText + "') AND (tweet = " + tweetId + ")");

            if(result.next()) {
                doesExist = true;
            }

            statement.close();
        } catch (SQLException e) {
            System.out.println("Could not test whether hashtag and tweet does exist in DB");
            e.printStackTrace();
        }

        return doesExist;
    }

    private void createHashtagForTweet(Status tweet, HashtagEntity hashtag) {
        try {
            PreparedStatement statement = connection.prepareStatement("" +
                    "INSERT INTO mandy_masterarbeit.twitter_tweet_hashtag(hashtag, tweet)" +
                    "VALUES (?, ?)");
            statement.setString(1, hashtag.getText());
            statement.setLong(2, tweet.getId());
            statement.execute();
            statement.close();
        } catch (SQLException e) {
            System.out.println("Hashtags could not be saved to DB for Hashtag " + hashtag.getText());
            e.printStackTrace();
        }
    }

    //---------------------------------    URLs      ------------------------------------//

    public void writeUrlContentToDB(Status tweet, String url, String urlContent) {
        if(!urlForTweetDoesExist(tweet.getId(), url)) {
            createUrlForTweet(tweet, url, urlContent);
        }
    }

    private boolean urlForTweetDoesExist(long tweetId, String url) {
        boolean doesExist = false;
        try {
            Statement statement = this.connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM mandy_masterarbeit.twitter_tweet_url WHERE (url = '" +
                    url + "') AND (tweet = " + tweetId + ")");

            if(result.next()) {
                doesExist = true;
            }

            statement.close();
        } catch (SQLException e) {
            System.out.println("Could not test whether url and tweet does exist in DB");
            e.printStackTrace();
        }

        return doesExist;
    }

    private void createUrlForTweet(Status tweet, String url, String urlContent) {
        try {
            PreparedStatement statement = this.connection.prepareStatement("" +
                    "INSERT INTO mandy_masterarbeit.twitter_tweet_url(url, content, tweet)" +
                    "VALUES (?, ?, ?)");
            statement.setString(1, url);
            statement.setString(2, urlContent);
            statement.setLong(3, tweet.getId());
            statement.execute();
            statement.close();
        } catch (SQLException e) {
            System.out.println("UrlContent could not be saved to DB for URL " + url);
            System.out.println(e.toString());
            //e.printStackTrace();
        }
    }

    //---------------------------------    SELECT     ------------------------------------//

    public Map<Long,String> selectTweetsCreatedAt(java.sql.Date createdAtDate) {
        Map<Long, String> tweetIdToContent = new HashMap<Long, String>();
        try {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT id, content FROM mandy_masterarbeit.twitter_tweet " +
                                                        "WHERE created_at = '2014-07-24'");

            while(result.next()) {
                long tweetId = result.getLong(1);
                String tweetContent = result.getString(2);
                tweetIdToContent.put(tweetId, tweetContent);
            }

            statement.close();
        } catch (SQLException e) {
            System.out.println("Could not select tweets from DB which are created at: " + createdAtDate + "!");
            e.printStackTrace();
        }
        return tweetIdToContent;
    }

    public static class LuceneManager {

        public static void test(String[] args) throws IOException, ParseException {
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);

            // 1. create the index
            Directory index = new RAMDirectory();

            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_40, analyzer);

            IndexWriter w = new IndexWriter(index, config);
            addDoc(w, "Lucene in Action", "193398817");
            addDoc(w, "Lucene for Dummies", "55320055Z");
            addDoc(w, "Managing Gigabytes", "55063554A");
            addDoc(w, "The Art of Computer Science", "9900333X");
            w.close();

            // 2. query
            String querystr = args.length > 0 ? args[0] : "dummie";

            // the "title" arg specifies the default field to use
            // when no field is explicitly specified in the query.
            Query q = new QueryParser(Version.LUCENE_40, "title", analyzer).parse(querystr);

            // 3. search
            int hitsPerPage = 10;
            IndexReader reader = DirectoryReader.open(index);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            // 4. display results
            System.out.println("Found " + hits.length + " hits.");
            for(int i=0;i<hits.length;++i) {
              int docId = hits[i].doc;
              Document d = searcher.doc(docId);
              System.out.println((i + 1) + ". " + d.get("isbn") + "\t" + d.get("title"));
            }

            // reader can only be closed when there
            // is no need to access the documents any more.
            reader.close();
        }

      private static void addDoc(IndexWriter w, String title, String isbn) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));

        // use a string field for isbn because we don't want it tokenized
        doc.add(new StringField("isbn", isbn, Field.Store.YES));
        w.addDocument(doc);
      }
    }
}