#!/usr/bin/perl -w

######################################################################
# kidsql_initall.pl
# Younghong "Hong" Cho <hongcho@sori.org>
# == History
# 2010-09-20 Created
######################################################################

use strict;
use lib '/home/soriorg/bin';
use KidSql;

print("Going to initialize all KIDS board DBs!!!\n");
print("ARE YOU SURE???\n");
getc() =~ /y/oi or print("aborted.\n"),exit(1);

my $bl = KidSql::GetBoardList();
defined($bl) or die("failed to get the board list\n");

print("dropping all tables...\n");
KidSql::DropAllTables();

foreach my $tab (sort(keys(%$bl))) {
    my ($t, $b);
    if ($tab =~ /^(\d+)_(.+)$/o) {
	$t = $1;
	$b = $2;
    } else {
	die("something seriously wrong: $tab...");
    }
    print("initializing $tab...");
    KidSql::UpdateBoardDB($b, $t, 1);
}

exit(0);
