#!/usr/bin/perl -w
######################################################################
# Copyright (c) 2001-2010, Younghong "Hong" Cho <hongcho@sori.org>.
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
#   1. Redistributions of source code must retain the above copyright notice,
# this list of conditions and the following disclaimer.
#   2. Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#   3. Neither the name of the organization nor the names of its contributors
# may be used to endorse or promote products derived from this software
# without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
# DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
# THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
######################################################################
# kidsql_job.pl
# == History
# 2010-09-20 Created
######################################################################

use strict;
use lib '/home/soriorg/bin';
use KidSql;

#print('kidsql_job skipped: ', scalar(localtime), "\n"),exit(0);

print('kidsql_job started: ', scalar(localtime), "\n");

my $bl = KidSql::GetBoardList();
defined($bl) or die("failed to get the board list\n");

foreach my $tab (sort(keys(%$bl))) {
    my ($t, $b);
    if ($tab =~ /^(\d+)_(.+)$/o) {
	$t = $1;
	$b = $2;
    } else {
	die("something seriously wrong: $tab...");
    }
    print("updating $tab...");
    KidSql::UpdateBoardDB($b, $t);
}

print('kidsql_job ended: ', scalar(localtime), "\n");
exit(0);
