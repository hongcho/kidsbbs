#!/usr/bin/perl -w

######################################################################
# kidsql_update1.pl
# Younghong "Hong" Cho <hongcho@sori.org>
# == History
# 2010-09-02 Created
######################################################################

use strict;
use lib '/home/soriorg/bin';
use KidSql;

my $b = shift;
my $t = shift;
my $init = shift;
defined($b) and defined($t) or die("need board, type\n");

if (defined($init) and $init) {
    my $tn = $t.'_'.$b;
    print("Going to initialize $tn!!!\n");
    print("ARE YOU SURE???\n");
    getc() =~ /y/oi or print("aborted.\n"),exit(1);
}

KidSql::UpdateBoardDB($b, $t, $init);

exit(0);
