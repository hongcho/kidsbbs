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

my $bl = KidSql::GetBoardList();
defined($bl) or die("failed to get the board list\n");

foreach my $tab (sort(keys(%$bl))) {
    print($tab, "\n");
}

exit(0);
