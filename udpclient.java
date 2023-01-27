import java.net.*;
import java.util.*;
  
public class udpclient
{
    public static void main( String args[] ) throws Exception 
    {
	InetAddress add = InetAddress.getByName("localhost");   
                                         
	DatagramSocket dsock = new DatagramSocket( );
	String message1 = "This is client calling";    
	byte arr[] = message1.getBytes( );  
	DatagramPacket dpack = new DatagramPacket(arr, arr.length, add, 7077);
	dsock.send(dpack);                                   // send the packet
	Date sendTime = new Date();                          // note the time of sending the message
  
	dsock.receive(dpack);                                // receive the packet
	String message2 = new String(dpack.getData( ));
	Date receiveTime = new Date( );   // note the time of receiving the message
	System.out.println((receiveTime.getTime( ) - sendTime.getTime( )) + " milliseconds echo time for " + message2);
    }
}