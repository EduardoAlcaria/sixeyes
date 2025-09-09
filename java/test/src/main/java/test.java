import java.io.*;
import java.util.Iterator;

public class test {
    public static void main(String[] args) throws IOException {

        //refactor t
        String magnet = '"' + "magnet:?xt=urn:btih:319E1B84A9029D57856C4367A7993E8C8C13FD00&dn=METAL+GEAR+SOLID+DELTA%3A+SNAKE+EATER+-+Digital+Deluxe+Edition+%28v1.1.1+%2B+Sneaking+DLC+Pack%2C+MULTi11%29+%5BFitGirl+Repack%2C+Selective+Download+-+from+53.7+GB%5D&tr=udp%3A%2F%2Fopentor.net%3A6969&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=udp%3A%2F%2Ftracker.theoks.net%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.ccp.ovh%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=https%3A%2F%2Ftracker.tamersunion.org%3A443%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.bt4g.com%3A2095%2Fannounce&tr=udp%3A%2F%2Fbt2.archive.org%3A6969%2Fannounce&tr=udp%3A%2F%2Fbt1.archive.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.filemail.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker1.bt.moack.co.kr%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Fcoppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.zer0day.to%3A1337%2Fannounce" + '"';
        System.out.println("Received magnet: " + magnet);

        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File("D:\\programming_WINDOWS_ONLY\\projects\\sixeyes\\python\\.venv\\Scripts"));

        String pythonScript = "D:\\programming_WINDOWS_ONLY\\projects\\sixeyes\\python\\Magnet.py";

        pb.command("cmd.exe", "/c", ".\\python.exe", pythonScript, magnet);

        Process process = pb.start();


        InputStream inputStream = process.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        File file = new File("D:\\programming_WINDOWS_ONLY\\projects\\sixeyes\\python\\log.json");

        BufferedReader jsonReader = new BufferedReader(new FileReader(file));
        String line1;
        while ((line1 = jsonReader.readLine()) != null) {
            System.out.println(line1);
        }

    }

}
