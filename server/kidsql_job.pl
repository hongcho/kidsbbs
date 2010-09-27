#!/usr/bin/perl -w

######################################################################
# kidsql_job.pl
# Younghong "Hong" Cho <hongcho@sori.org>
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
