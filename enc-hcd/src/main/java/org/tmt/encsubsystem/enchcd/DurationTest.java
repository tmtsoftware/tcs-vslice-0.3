package org.tmt.encsubsystem.enchcd;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class DurationTest {

	public static void main(String[] args) {
		System.out.println(Instant                // Represent a moment in UTC. 
				.now()                 // Capture the current moment. Returns a `Instant` object. 
				.truncatedTo(          // Lop off the finer part of this moment. 
				    ChronoUnit.MICROS  // Granularity to which we are truncating.
				));// Returns another `Instant` object rather than changing the original, per the immutable objects pattern.
		
		System.out.println(ZonedDateTime.now(ZoneId.of( "America/Montreal")));		
		Instant previous = Instant.now();
		Instant current = Instant.now();
		
		int i =0;
		while(current.compareTo(previous)<=0) {
		    current = Instant.now();
		    i++;
			continue;
		}
		
		System.out.println("Iteration=" + i+" , "+Duration.between(previous, current).toNanos());

		long previousNano = System.nanoTime();
		long currentNano = System.nanoTime();
		
		 i =0;
		while(currentNano<=previousNano) {
		    currentNano =System.nanoTime();
		    i++;
			continue;
		}
		
		System.out.println("Iteration=" + i+" , "+ (currentNano-previousNano));
		
		
		
	}

}
