/*
 * test.cpp
 *
 *  Created on: 12.09.2017
 *      Author: Viktor Csomor
 */

#include "test.h"

#include <stdio.h>
#include <unistd.h>

void wait(int sleepTime) {
	for (int i = 1; i <= sleepTime; i++) {
		sleep(1);
		if (i != sleepTime) {
			printf("in progress\n");
			fflush(stdout);
		}
	}
	return;
}
