import ai.lucidtech.las.sdk.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;


public class ClientTest {
    private static final String CONFIG_RELATIVE_PATH = "config.properties";

    private Client client;
    private Properties config;

    @Before
    public void setUp() {
        String configPath = this.getResourcePath(ClientTest.CONFIG_RELATIVE_PATH);

        try(FileInputStream input = new FileInputStream(configPath)) {
            this.config = new Properties();
            this.config.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Credentials credentials = new Credentials(
            this.config.getProperty("clientId"),
            this.config.getProperty("clientSecret"),
            this.config.getProperty("apiKey"),
            this.config.getProperty("authEndpoint"),
            this.config.getProperty("apiEndpoint")
        );
        this.client = new Client(credentials);
    }

    private String getResourcePath(String relativePath) {
        return getClass().getResource(relativePath).getFile();
    }

    private String[] toArray(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).toArray(String[]::new); }

    @Test
    public void testGetDocument() throws IOException {
        String documentId = UUID.randomUUID().toString();
        JSONObject document = this.client.getDocument(documentId);
        Assert.assertTrue(document.has("consentId"));
        Assert.assertTrue(document.has("contentType"));
        Assert.assertTrue(document.has("documentId"));
    }

    @Test
    public void testCreateDocument() throws IOException {
        String[] documentMimeTypes = this.toArray(this.config.getProperty("document.mime.types"));

        for (String documentMimeType : documentMimeTypes) {
            byte[] content = UUID.randomUUID().toString().getBytes();
            ContentType contentType = ContentType.fromString(documentMimeType);
            String consentId = UUID.randomUUID().toString();

            JSONObject document = this.client.createDocument(content, contentType, consentId);
            Assert.assertTrue(document.has("consentId"));
            Assert.assertTrue(document.has("contentType"));
            Assert.assertTrue(document.has("documentId"));
        }
    }

    @Test
    public void testCreatePrediction() throws IOException, URISyntaxException {
        String[] modelNames = this.toArray(this.config.getProperty("model.names"));
        String[] documentPaths = this.toArray(this.config.getProperty("document.paths"));
        String[] documentMimeTypes = this.toArray(this.config.getProperty("document.mime.types"));

        for (int i = 0; i < documentPaths.length; ++i) {
            String modelName = modelNames[i];
            String documentId = UUID.randomUUID().toString();

            JSONObject prediction = this.client.createPrediction(documentId, modelName);
            System.out.println(prediction);

            JSONArray fields = prediction.getJSONArray("predictions");
            Assert.assertNotNull(fields);

            StreamSupport.stream(fields.spliterator(), false)
                    .map(o -> (JSONObject)o)
                    .forEach(field -> {
                        Assert.assertNotNull(field.get("label"));
                        Assert.assertNotNull(field.get("value"));
                        float confidence = field.getFloat("confidence");
                        Assert.assertTrue(confidence >= 0.0);
                        Assert.assertTrue(confidence <= 1.0);
                    });
        }
    }

    private JSONObject createField(String label, String value) {
        JSONObject field = new JSONObject();
        field.put("label", label);
        field.put("value", value);
        return field;
    }

    private static void assertFeedbackItem(JSONObject feedbackItem) {
        Assert.assertNotNull(feedbackItem.get("label"));
        Assert.assertNotNull(feedbackItem.get("value"));
    }

    private static void assertFeedbackResponse(JSONObject feedbackResponse) {
        Assert.assertNotNull(feedbackResponse.get("documentId"));
        Assert.assertNotNull(feedbackResponse.get("consentId"));
        JSONArray feedback = feedbackResponse.getJSONArray("feedback");
        Assert.assertNotNull(feedback);

        StreamSupport.stream(feedback.spliterator(), false)
                .map(o -> (JSONObject)o)
                .forEach(ClientTest::assertFeedbackItem);
    }

    @Test
    public void testUpdateDocument() throws IOException {
        String[] documentMimeTypes = this.toArray(this.config.getProperty("document.mime.types"));

        for (String documentMimeType : documentMimeTypes) {
            byte[] content = UUID.randomUUID().toString().getBytes();
            ContentType contentType = ContentType.fromString(documentMimeType);
            String consentId = UUID.randomUUID().toString();
            JSONObject document = this.client.createDocument(content, contentType, consentId);
            String documentId = document.getString("documentId");

            JSONObject feedback = new JSONObject();
            List<JSONObject> fieldList = Arrays.asList(
                this.createField("total_amount", "123.00"),
                this.createField("purchase_date", "2019-05-23")
            );
            JSONArray fields = new JSONArray(fieldList);
            feedback.put("feedback", fields);

            JSONObject feedbackResponse = this.client.updateDocument(documentId, feedback);
            ClientTest.assertFeedbackResponse(feedbackResponse);
        }
    }

    @Ignore
    @Test
    public void testDeleteConsentId() throws IOException {
        String[] documentMimeTypes = this.toArray(this.config.getProperty("document.mime.types"));

        for (String documentMimeType : documentMimeTypes) {
            byte[] content = UUID.randomUUID().toString().getBytes();
            ContentType contentType = ContentType.fromString(documentMimeType);
            String consentId = UUID.randomUUID().toString();
            this.client.createDocument(content, contentType, consentId);

            JSONObject response = this.client.deleteConsent(consentId);
            Assert.assertNotNull(response.getString("consentId"));
            JSONArray documentIds = response.getJSONArray("documentIds");
            Assert.assertNotNull(documentIds);
        }
    }
}
