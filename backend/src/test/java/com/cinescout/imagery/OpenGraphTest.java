package com.cinescout.imagery;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class OpenGraphTest {

    private static final URI PAGE = URI.create("https://venue.example/rooftop/");

    @Test
    void findsTheSharingImageWhateverTheOrderAndQuotesOfItsAttributes() {
        assertThat(OpenGraph.imageIn("<head><meta property=\"og:image\" content=\"https://cdn.example/a.jpg\"></head>", PAGE))
                .contains("https://cdn.example/a.jpg");
        assertThat(OpenGraph.imageIn("<META CONTENT='https://cdn.example/b.jpg' PROPERTY='og:image' />", PAGE))
                .contains("https://cdn.example/b.jpg");
        assertThat(OpenGraph.imageIn("<meta name=\"twitter:image\" content=\"https://cdn.example/c.jpg\">", PAGE))
                .contains("https://cdn.example/c.jpg");
    }

    @Test
    void prefersTheSecureOpenGraphImageToTheOthers() {
        String html = """
                <meta name="twitter:image" content="https://cdn.example/twitter.jpg">
                <meta property="og:image" content="http://cdn.example/plain.jpg">
                <meta property="og:image:secure_url" content="https://cdn.example/secure.jpg">""";

        assertThat(OpenGraph.imageIn(html, PAGE)).contains("https://cdn.example/secure.jpg");
    }

    @Test
    void resolvesARelativeAddressAndUnescapesAmpersands() {
        assertThat(OpenGraph.imageIn("<meta property=\"og:image\" content=\"/img/roof.jpg?w=1200&amp;h=630\">", PAGE))
                .contains("https://venue.example/img/roof.jpg?w=1200&h=630");
    }

    @Test
    void offersNothingForAPageWithoutOneOrWithAnAddressThatIsNotAWebAddress() {
        assertThat(OpenGraph.imageIn("<html><head><title>Rooftop</title></head></html>", PAGE)).isEmpty();
        assertThat(OpenGraph.imageIn("<meta property=\"og:image\" content=\"javascript:alert(1)\">", PAGE)).isEmpty();
        assertThat(OpenGraph.imageIn("<meta property=\"og:image\" content=\"data:image/png;base64,AAAA\">", PAGE)).isEmpty();
        assertThat(OpenGraph.imageIn("<meta property=\"og:image\" content=\"\">", PAGE)).isEmpty();
        assertThat(OpenGraph.imageIn("<meta property=\"og:image\" content=\"https://cdn.example/" + "x".repeat(1000) + "\">", PAGE)).isEmpty();
    }
}
