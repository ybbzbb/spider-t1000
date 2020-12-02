package ybbzbb.github.spider.crawl.download.http;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UserAgentRandom {
    private static List<String> userAgentArrays = readData();

    private static final Random random = new Random();

    private static int RANDOM_SIZE = userAgentArrays.size();

    public static String getRandomUserAgent(){
        if (RANDOM_SIZE == 0 || CollectionUtils.isEmpty(userAgentArrays)){
            userAgentArrays = readData();
            RANDOM_SIZE = userAgentArrays.size();
        }

        if (RANDOM_SIZE == 0) {
            return null;
        }

        int i = random.nextInt(RANDOM_SIZE);
        return userAgentArrays.get(i);
    }


    private static List<String> readData(){

        final List<String> userAgents = new ArrayList<>(12000);

        ClassPathResource classPathResource =  new ClassPathResource("user-agent.txt");

        try(
                InputStream inputStream = classPathResource.getInputStream();
                InputStreamReader read = new InputStreamReader(inputStream,"UTF-8");
                final BufferedReader br = new BufferedReader(read);
        ){

            String line = "";
            while ( (line = br.readLine()) != null){
                if (StringUtils.isNotBlank(line)) {
                    userAgents.add(line);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        return userAgents;
    }

    public static void main(String[] args) {
        readData();
    }

}
