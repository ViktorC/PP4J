/*
 * test.cpp
 *
 *  Created on: 12.09.2017
 *      Author: Viktor Csomor
 */

#include "test.h"

#include <stdio.h>
#include <unistd.h>

void doStuff(int seconds) {
	for (int i = 1; i <= seconds; i++) {
		sleep(1);
		if (i != seconds) {
			printf("in progress\n");
			fflush(stdout);
		}
	}
	return;
}
