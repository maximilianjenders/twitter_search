package org.twittersearch.app.search_engine;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Mandy Roick on 02.10.2014.
 */
public class ElasticSearchManager {

    Client client;

    public static void main(String[] args) {
        //Node node = NodeBuilder.nodeBuilder().local(true).node();
        //Client client = node.client();
        //Settings settings = ImmutableSettings.settingsBuilder().build();
        //TransportClient client = new TransportClient(settings);
        //client = client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));

        //ElasticSearchIndexer.indexFromDB("2014-10-21", client);

        //GetResponse getResponse = client.prepareGet("twitter", "tweet", "518695575102164993")
        //        .execute()
        //        .actionGet();
        //System.out.println(getResponse.getSource());

        //ElasticSearchManager esManager = new ElasticSearchManager();
        //esManager.searchFor("politics");
        //client.close();

        ElasticSearchManager esManager = new ElasticSearchManager();
        esManager.addToIndexWithUrls("2014-10-21");
        //esManager.addToIndex("2014-12-04");
    }

    public ElasticSearchManager() {
        Settings settings = ImmutableSettings.settingsBuilder().build();
        TransportClient transportClient = new TransportClient(settings);
        transportClient = transportClient.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
        this.client = transportClient;
    }

    public void addToIndexWithUrls(String date) {
        ElasticSearchIndexer.indexFromDBWithUrls(date, this.client);
    }

    public void addToIndex(String date) { ElasticSearchIndexer.indexFromDB(date, this.client); }

    public SearchHits searchFor(String query) {
        SearchResponse response = this.client.prepareSearch().setQuery(QueryBuilders.matchQuery("content", query))
                                                             .setFrom(0)
                                                             .setSize(60).execute().actionGet();
        return response.getHits();
    }

    public SearchHits searchFor(String query, String date) {
        // TODO: use the date and search only in tweets from that date => need to google how elastic search handles dates
        FilterBuilder filterBuilder = FilterBuilders.termFilter("created_at", date);
        QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery(query, "content", "url_content");
        FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(queryBuilder, filterBuilder);

        SearchResponse response = this.client.prepareSearch().setQuery(filteredQueryBuilder)
                .setFrom(0)
                .setSize(100).execute().actionGet();
        return response.getHits();
    }

    public SearchHits searchForInSample(String query, Collection<String> sampledIDs) {
        FilterBuilder filterBuilder = FilterBuilders.idsFilter("tweet").addIds(sampledIDs.toArray(new String[sampledIDs.size()]));
        QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery(query, "content", "url_content"); // first query, than fields I query on
        FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(queryBuilder, filterBuilder);

        SearchResponse response = this.client.prepareSearch().setQuery(filteredQueryBuilder)
                .setFrom(0)
                .setSize(100).execute().actionGet();

        return response.getHits();
    }

    public SearchHits searchForInSample(String[] query, Collection<String> sampledIDs) {
        FilterBuilder filterBuilder = FilterBuilders.idsFilter("tweet").addIds(sampledIDs.toArray(new String[sampledIDs.size()]));
        QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery(query, "content", "url_content"); // first query, than fields I query on
        FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(queryBuilder, filterBuilder);

        SearchResponse response = this.client.prepareSearch().setQuery(filteredQueryBuilder)
                .setFrom(0)
                .setSize(100).execute().actionGet();

        return response.getHits();
    }

    private SearchHits searchForUrlContentMissing(String[] tweetIds) {
        FilterBuilder idFilterBuilder = FilterBuilders.idsFilter("tweet").addIds(tweetIds);
        FilterBuilder existsFilterBuilder = FilterBuilders.existsFilter("url_content");
        FilterBuilder filterBuilder = FilterBuilders.boolFilter().must(idFilterBuilder).mustNot(existsFilterBuilder);

        SearchResponse response = this.client.prepareSearch().setPostFilter(filterBuilder)
                .setFrom(0).setSize(10000).execute().actionGet();
        return response.getHits();
    }

    private long searchForUrlContentPresent(String[] tweetIds) {
        FilterBuilder idFilterBuilder = FilterBuilders.idsFilter("tweet").addIds(tweetIds);
        FilterBuilder existsFilterBuilder = FilterBuilders.existsFilter("url_content");
        FilterBuilder filterBuilder = FilterBuilders.boolFilter().must(idFilterBuilder).must(existsFilterBuilder);

        SearchResponse response = this.client.prepareSearch().setPostFilter(filterBuilder)
                .setFrom(0).setSize(10000).execute().actionGet();
        return response.getHits().getTotalHits();
    }

    public Long[] tweetsInESWithoutUrlContent(Collection<Long> tweetIds) {
        String[] tweetIdStrings = new String[tweetIds.size()];
        int i = 0;
        for (Long tweetId : tweetIds) {
            tweetIdStrings[i] = Long.toString(tweetId);
            i++;
        }

        SearchHits tweetsWithoutUrlContent = searchForUrlContentMissing(tweetIdStrings);
        long tweetsWithUrlContent = searchForUrlContentPresent(tweetIdStrings);
        System.out.println(tweetsWithUrlContent + " tweets are already indexed.");
        System.out.println(tweetsWithoutUrlContent.totalHits() + " tweets still need to be indexed.");

        List<Long> tweetIdsWithoutUrlContent = new ArrayList<Long>();
        for (SearchHit tweetWithoutUrlContent : tweetsWithoutUrlContent) {
            tweetIdsWithoutUrlContent.add(Long.valueOf(tweetWithoutUrlContent.getId()));
        }

        return tweetIdsWithoutUrlContent.toArray(new Long[tweetIdsWithoutUrlContent.size()]);
    }

    public SearchHits searchForAllTweetsContainingQueryTerm(String[] query, String date) {
        FilterBuilder filterBuilder = FilterBuilders.termFilter("created_at", date);
        //QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery("politics", "content", "url_content");
        QueryBuilder queryBuilder = QueryBuilders.termsQuery("content", query).minimumMatch(1);
        FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(queryBuilder, filterBuilder);

        SearchResponse response = this.client.prepareSearch().setQuery(filteredQueryBuilder)
                .setFrom(0).setSize(1000000).execute().actionGet();
        return response.getHits();
    }

    public List<String> getTweetsContainingQueryTerm(String[] query, String date) {
        SearchHits tweetsContainingQueryTerm = searchForAllTweetsContainingQueryTerm(query, date);

        List<String> tweets = new ArrayList<String>();
        for (SearchHit tweetContainingQueryTerm : tweetsContainingQueryTerm) {
            //String tweet = tweetContainingQueryTerm.getSourceAsString();
            String tweet = (String) tweetContainingQueryTerm.sourceAsMap().get("content");

            tweets.add(tweet);
        }

        return tweets;
    }

    public long documentFrequency(String term, String date) {
        FilterBuilder filterBuilder = FilterBuilders.termFilter("created_at", date);
        //QueryBuilder queryBuilder = QueryBuilders.multiMatchQuery("politics", "content", "url_content");
        QueryBuilder queryBuilder = QueryBuilders.wildcardQuery("content", "*" + term + "*");
        FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(queryBuilder, filterBuilder);

        SearchResponse response = this.client.prepareSearch().setQuery(filteredQueryBuilder).execute().actionGet();
        return response.getHits().totalHits();
    }

    public long numberOfDocuments(String date) {
        FilterBuilder filterBuilder = FilterBuilders.termFilter("created_at", date);
        SearchResponse response = this.client.prepareSearch().setPostFilter(filterBuilder).execute().actionGet();
        return response.getHits().totalHits();
    }
}
