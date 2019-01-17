package org.tmt.tcs;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class DurationTest {

	public static void main(String[] args) throws InterruptedException {
		System.out.println(Instant                // Represent a moment in UTC. 
				.now()                 // Capture the current moment. Returns a `Instant` object. 
				.truncatedTo(          // Lop off the finer part of this moment. 
				    ChronoUnit.MICROS  // Granularity to which we are truncating.
				));// Returns another `Instant` object rather than changing the original, per the immutable objects pattern.
		
		System.out.println(ZonedDateTime.now(ZoneId.of( "America/Montreal")));		
		Instant previous = Instant.now();
		Thread.sleep(1,125);
		Instant current = Instant.now();


		System.out.println(Duration.between(previous, current).toNanos());
		Long l = Duration.between(previous, current).toNanos();
		l=234367000L;
		System.out.println(l.doubleValue());
		System.out.println(l.doubleValue()/1000000);

		
		
	}

}
