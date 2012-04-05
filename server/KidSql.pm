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
# KidSql.pm
# == History
# 2010-09-02 Created from KidsLib.pm.
######################################################################

package KidSql;
use strict;
use Encode;
use LWP::UserAgent;
use DBI;
# The default MD5 didn't quite work.
use lib '/home/soriorg/perlmod/usr/lib64/perl5/5.8.8/x86_64-linux-thread-multi';
use Digest::MD5 qw(md5_hex);
use lib '.';
use KidSqlDBInfo;

our @EXPORT = qw(GetBoardList
		 GetBoardListFromDB
		 DropAllTables
		 UpdateBoardDB);

######################################################################
# Kids Constants.

#my $KIDS_HOST = 'kidsb.net';
my $KIDS_HOST = 'kids.kornet.net';
my $KIDS_BASE = 'http://'.$KIDS_HOST;
my $KIDS_CGI = $KIDS_BASE.'/cgi-bin';
my $KIDS_BLCGI = $KIDS_CGI.'/Boardlist?';
my $KIDS_BLIST = $KIDS_BASE.'/blist.html';

my $KIDS_PSTR = '&Position=';
my $KIDS_NSTR = '&Num=';

my $AGENT_STRBASE = 'KidSqlGet 0/1 (http://sori.org/kids/) ';

my $MAX_TRY = 3;

######################################################################
# String Constants.

sub utf82euckr
{
    my ($s) = @_;
    Encode::from_to($s, 'UTF8', 'EUC-KR');
    return $s;
}

sub euckr2utf8
{
    my ($s) = @_;
    Encode::from_to($s, 'EUC-KR', 'UTF8');
    return $s;
}

sub k2u_hash
{
    my ($e, $u) = @_;
    foreach my $k (keys(%$e)) {
	my $v = $$e{$k};
	Encode::from_to($k, 'EUC-KR', 'UTF8');
	$$u{$k} = $v;
    }
}


my @S_DAY = ('Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat');
my @S_MON = ('Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
	     'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec');
my %N_MON = ('Jan' => 1, 'Feb' => 2, 'Mar' => 3, 'Apr' => 4, 'May' => 5,
	     'Jun' => 6, 'Jul' => 7, 'Aug' => 8, 'Sep' => 9, 'Oct' => 10,
	     'Nov' => 11, 'Dec' => 12);

my %K_DAY = ('�Ͽ���' => 'Sun',
	     '������' => 'Mon',
	     'ȭ����' => 'Tue',
	     '������' => 'Wed',
	     '�����' => 'Thu',
	     '�ݿ���' => 'Fri',
	     '�����' => 'Sat',
	     '��' => 'Sun',
	     '��' => 'Mon',
	     'ȭ' => 'Tue',
	     '��' => 'Wed',
	     '��' => 'Thu',
	     '��' => 'Fri',
	     '��' => 'Sat',
	     '(��)' => 'Sun',
	     '(��)' => 'Mon',
	     '(ȭ)' => 'Tue',
	     '(��)' => 'Wed',
	     '(��)' => 'Thu',
	     '(��)' => 'Fri',
	     '(��)' => 'Sat');

my %K_AMPM = ('����' => 0, '����' => 12);

# (year, month, mday, wday, ampm, hour, min, sec)
my $K_DATETMPL = '^\s*(\d+)��\s+(\d+)��\s+(\d+)��\s+(\S+)\s+(\S+)\s+(\d+)��\s+(\d+)��\s+(\d+)��\s*$';

my %BTYPES = (Boardname => 0, writer => 1, writer_p => 2);
my @BNAME1 = ('Boardname=', 'writer=', 'writer_p=');
my @BNAME2 = ('Article=', 'Article_w=', 'Article_p=');

my %B_KMAP = ('����ȸ' => 'zDongWuHoe',
	      '��' => 'zMeot',
	      '��' => 'zSan',
	      '��' => 'zSul',
	      '����' => 'zYugA');

######################################################################
# MySql Constants

my $mysql_host = KidSqlDBInfo::GetDBHost();
my $mysql_user = KidSqlDBInfo::GetDBUser();
my $mysql_passwd = KidSqlDBInfo::GetDBPasswd();
my $mysql_db = KidSqlDBInfo::GetDBName();

my $dbi_db = 'dbi:mysql:'.$mysql_db;
my $dbi_user = $mysql_user;
my $dbi_passwd = $mysql_passwd;

######################################################################
# SQL Constants.

my $K_MINCNT = 17;
my $K_MAXCNT = 2000;
my $K_MINDAYS = 7;		# 1 week(s)
my $K_MAXDAYS = 31;		# 1 month(s)

my $MAX_USERNAME = 12;
my $MAX_AUTHOR = 40;
my $MAX_GUESTKEY = 32;
my $MAX_TITLE = 40;
my $MAX_THREAD = 32;

my %A_TABDEF = (a0_seq => "INT NOT NULL",
		a1_username => "CHAR($MAX_USERNAME) NOT NULL",
		a2_author => "VARCHAR($MAX_AUTHOR) NOT NULL",
		a3_guestkey => "VARCHAR($MAX_GUESTKEY)",
		a4_date => "DATETIME NOT NULL",
		a5_title => "VARCHAR($MAX_TITLE)",
		a6_thread => "CHAR($MAX_THREAD) NOT NULL",
		a7_body => "TEXT");
my $A_TABDEF_EXTRA = 'PRIMARY KEY(a0_seq),INDEX thread(a6_thread),INDEX user(a1_username)';
my $A_SCHEMA = TabDefSchema(\%A_TABDEF, $A_TABDEF_EXTRA);
my $A_NAMES = TabDefNames(\%A_TABDEF);
my $A_VALUES = TabDefValues(\%A_TABDEF);

my $S_TZ_LOCAL = 'US/Mountain';
my $S_TZ_KST = 'Asia/Seoul';
my $S_CURDATE_KST = "CONVERT_TZ(NOW(), '$S_TZ_LOCAL', '$S_TZ_KST')";
my $S_CURDATE_UTC = "CONVERT_TZ(NOW(), '$S_TZ_LOCAL', 'UTC')";

# ** Threaded View **
# SELECT a0_seq,a1_username,a4_date,a5_title
#   FROM (SELECT * FROM 0_anonymous ORDER BY a0_seq desc) AS t
#   GROUP BY a6_thread ORDER BY a0_seq DESC;

# ** Thread List View **
# SELECT a0_seq,a1_username,a4_date,a5_title FROM 0_anonymous
#   WHERE a6_thread='085eaea5080f3ab18c0cb8f300165794'
#   ORDER BY a0_seq DESC;

# ** By user **
# SELECT a0_seq,a1_username,a4_date,a5_title,count(*) AS thrcnt,a6_thread
#   FROM (SELECT * FROM 0_anonymous ORDER BY a0_seq desc) AS t
#   GROUP BY a1_username ORDER BY thrcnt DESC,a0_seq DESC;

######################################################################
# Local variables.

my $AGENT = new LWP::UserAgent;
my $agent_set = 0;

######################################################################
# Date stuff...

my @MONTH2DAYS = (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334);

sub IsLeapYear
{
    my ($y) = @_;
    $y % 4 and return 0;
    $y % 100 and return 1;
    $y % 400 or return 1;
    return 0;
}

sub Date2Days
{
    my ($Y, $M, $D) = @_;
    my $t = 0;
    # from 1970...
    for (my $i = 1970; $i < $Y; ++$i) {
	$t += 365 + IsLeapYear($i);
    }
    $t += $MONTH2DAYS[$M-1];
    $M >= 3 and $t += IsLeapYear($Y);
    $t += $D;
    return $t;
}

sub GetDaysFromDateSql
{
    my ($ds) = @_;
    if ($ds =~ /^(\d+)-(\d+)-(\d+) (\d+):(\d+):(\d+)$/o) {
	return Date2Days($1, $2, $3);
    }
    return undef;
}

######################################################################
# Escape XML text.
sub EscapeXmlText
{
    my ($s) = @_;
    $s =~ s/&/&amp;/g;
    $s =~ s/</&#60;/g;
    return $s;
}

######################################################################
# Encode URL.
sub EncodeUrl
{
    my ($s) = @_;
    my @cs = split(//, $s);
    $s = '';
    while (scalar(@cs)) {
	my $c = shift(@cs);
	my $n = ord($c);
	if ($c eq '%' or $n < 0x20 or $n > 0x7f) {
	    $s .= sprintf("%%%02X", $n);
	} else {
	    $s .= $c;
	}
    }
    return $s;
}

######################################################################
# Revert some KIDS web format for the header
# - input: reference to a string
sub DecodeKidsHeaderFormat
{
    my ($l) = @_;
    $$l =~ s/\s*<br \/>//g;
    $$l =~ s/&lt;/</g;
    $$l =~ s/&gt;/>/g;		# 2012-02-25
    $$l =~ s/&amp;/&/g;		# 2012-03-02
    $$l =~ s/&nbsp;/ /g;
}

######################################################################
# Revert some KIDS web foramt for the body
# - input: reference to a string
sub DecodeKidsBodyFormat
{
    my ($l) = @_;
    $$l =~ s/\s*<br \/>/<br\/>/g;
    $$l =~ s/&amp;gt;/>/g;	# 2012-02-07
    $$l =~ s/&gt;/>/g;		# 2012-03-30
}

######################################################################
# Get the date string in RFC 822.
sub GetDateString822
{
    my ($t) = @_;
    return sprintf("%s, %02d %s %d %02d:%02d:%02d GMT",
		   $S_DAY[$t->[6]],
		   $t->[3], $S_MON[$t->[4]], $t->[5] + 1900,
		   $t->[2], $t->[1], $t->[0]);
}

######################################################################
# Correct Hour.
sub ConvertKidsHour
{
    my ($ampm, $h) = @_;
    return $K_AMPM{$ampm} + ($h % 12);
}

######################################################################
# Convert the KIDS date string to RFC 822 string.
sub ConvertKidsDate822
{
    my ($d) = @_;
    if ($d =~ /$K_DATETMPL/o) {
	$d = sprintf("%s, %02d %s %d %02d:%02d:%02d KST",
		     $K_DAY{$4},
		     $3, $S_MON[$2 - 1], $1,
		     ConvertKidsHour($5, $6), $7, $8 );
    }
    return $d;
}

######################################################################
# Convert the KIDS date string to SQL DATETIME string.
sub ConvertKidsDateSql
{
    my ($d) = @_;
    if ($d =~ /$K_DATETMPL/o) {
	$d = sprintf("%04d-%02d-%02d %02d:%02d:%02d",
		     $1, $2, $3, ConvertKidsHour($5, $6), $7, $8 );
    }
    return $d;
}

######################################################################
# Convert the date info to a number.
sub ConvertDateToNum
{
    my ($Y, $M, $D, $h, $m, $s) = @_;
    my $v = $Y;
    $v = $v * 12 + $M;
    $v = $v * 31 + $D;
    $v = $v * 24 + $h;
    $v = $v * 60 + $m;
    $v = $v * 60 + $s;
    return $v;
}

######################################################################
# Convert the date string to a number.
sub ConvertDate822ToNum
{
    my ($d) = @_;
    my $v = 0;
    if ($d =~ /^\w*,\s+(\d+)\s+(\w+)\s+(\d+)\s+(\d+):(\d+):(\d+)/o) {
	$v = ConvertDateToNum($3, $N_MON{$2}, $1, $4, $5, $6);
    }
    return $v;
}

######################################################################
# Convert the date string to a number.
sub ConvertDateSqlToNum
{
    my ($d) = @_;
    my $v = 0;
    if ($d =~ /^(\d+)-(\d+)-(\d+)\s+(\d+):(\d+):(\d+)/o) {
	$v = ConvertDateToNum($1, $2, $3, $4, $5, $6);
    }
    return $v;
}

######################################################################
# Set up the user agent string.
sub SetUserAgentString
{
    $agent_set or $AGENT->agent($AGENT_STRBASE . $AGENT->agent);
    $agent_set = 1;
}

######################################################################
# Get the board URL.
sub GetBoardUrl
{
    my ($b, $t, $p) = @_;
    $p =~ /^\d+$/o and $p = 'P'.$p;
    return $KIDS_BLCGI.$BNAME1[$t].EncodeUrl($b).$KIDS_PSTR.$p;
}

######################################################################
# Get the article URL.
sub GetArticleUrl
{
    my ($b, $t, $n) = @_;
    return $KIDS_BLCGI.$BNAME2[$t].EncodeUrl($b).$KIDS_NSTR.$n;
}

######################################################################
# Get the item/article URL.
sub GetItemUrl
{
    my ($url) = @_;
    $url = $KIDS_CGI.'/'.$url;
    # replace the Position parameter to "Last"
    if ($url =~ /^(.+)$KIDS_PSTR(Last|P\d+)(.*)$/o) {
	my ($pre, $post) = ($1, $3);
	my $pos = 'Last';
	if ($pre =~ /[&?]Num=(\d+)/o) {
	    $pos = 'P'.$1;
	} elsif ($post =~ /[&?]Num=(\d+)/o) {
	    $pos = 'P'.$1;
	}
	$url = $pre.$KIDS_PSTR.$pos.$post;
    }
    # re-encode the board name.
    if ($url =~ /^(.+)(\?Article(_[pw])?=)([^&]*)(.*)$/o) {
	my ($pre, $param, $board, $post) = ($1, $2, $4, $5);
	$url = $pre.$param.EncodeUrl($board).$post;
    }
    return $url;
}

######################################################################
# Get the board page.
sub GetBoardPage
{
    my ($b, $t, $p) = @_;
    my $url = GetBoardUrl($b, $t, $p);
    defined($url) or return undef;
    my $bp = {board => $b,
	      type => $t,
	      list => []};
    SetUserAgentString();
    my $rq = new HTTP::Request(GET => $url);
    my $try = $MAX_TRY;
    while ($try-- > 0) {
	my $rs = $AGENT->request($rq);
	next if (!$rs->is_success);
	foreach my $l (split(/\n/, $rs->content)) {
	    $l = utf82euckr($l);# Keep things in EUC-KR
	    if ($l =~ /^\s*<tr><td align=center><a href=.+?>([^<]+)<\/a><td bgcolor=pink>(.+?)<td>&nbsp;(.+?)<td>\s*(\d+\s*\/\s*\d+)\s*<td align=right>\s*(\S+)\s*<td><a href=(.+?)>&nbsp;(.+?)<\/a><\/tr>/oi) {
		my $e;
		$e->{seq} = $1;
		$e->{id} = $2;
		$e->{name} = $3;
		$e->{date} = $4;
		$e->{view} = $5;
		$e->{title} = $7;
		$e->{url} = $6;
		next if ($e->{id} =~ /=======/o); # deleted
		next if ($e->{id} =~ /^\s*$/o);	# no id?
		next if ($e->{date} =~ /^\s*0\s*\/\s*0\s*$/o); # bad date

		# check incomplete hangul code at the end
		if (length($e->{title}) >= $MAX_TITLE
		    and substr($e->{title}, $MAX_TITLE - 1, 1) eq '_') {
		    $e->{title} = substr($e->{title}, 0, $MAX_TITLE - 1);
		}
		push(@{$bp->{list}}, $e);
	    }
	}
	last;
    }
    if ($try < 0) {
	print(">> request failed: $url\n");
	return undef;
    }
    return $bp;
}

######################################################################
# Get the article page.
sub GetArticlePage
{
    my ($b, $t, $n, $u, $s) = @_;
    defined($u) or $u = '';
    defined($s) or $s = '';
    my $url = GetArticleUrl($b, $t, $n);
    defined($url) or return undef;
    my $a = {guid => $url,
	     board => $b,
	     seq => $n,
	     author => $u,
	     guestkey => '',
	     date => '',
	     datesql => '',
	     title => $s,
	     body => ''};
    SetUserAgentString();
    my $rq = new HTTP::Request(GET => $url);
    my $try = $MAX_TRY;
    while ($try-- > 0) {
	my $rs = $AGENT->request($rq);
	next if (!$rs->is_success);
	my $st = 0;
	my $skip = 1;
	foreach my $l (split(/\n/, $rs->content)) {
	    $l = utf82euckr($l);# Keep things in EUC-KR
	    if ($st == 0) {
		($l =~ /<td>/oi) and $st = 1;
	    } elsif ($st == 1) {
		DecodeKidsHeaderFormat(\$l);
		if ($l =~ /\(By\):\s*(.+?)\s*$/o) {
		    $a->{author} = $1;
		} elsif ($l =~ /Guest Auth Key:\s*(.+?)\s*$/o) {
		    $a->{guestkey} = $1;
		} elsif ($l =~ /\(Date\):\s*(.+?)\s*$/o) {
		    $a->{date} = $1;
		} elsif ($l =~ /\(Title\):\s*(.+?)\s*$/o) {
		    $a->{title} = $1;
		} elsif ($l eq '') {
		    $skip-- > 0 or $st = 2;
		}
	    } elsif ($st == 2) {
		last if ($l =~ /<\/td>/oi or $l eq '');
		DecodeKidsBodyFormat(\$l);
		$a->{body} .= $l;
	    } else {
		print("GetArticlePage: invalid state: $st\n"),exit(-1);
	    }
	}
	# make sure we have minimum stuff
	if ($a->{title} eq '' or $a->{author} eq '') {
	    print(">> parse error: $url\n");
	    return undef;
	}
	# convert date.
	$a->{datesql} = ConvertKidsDateSql($a->{date})
	    unless ($a->{date} eq '');
	$a->{guid} = $url.';'.ConvertDateSqlToNum($a->{datesql});
	last;
    }
    if ($try < 0) {
	print(">> request failed: $url\n");
	return undef;
    }
    return $a;
}

######################################################################
# Select the username
# - �ƹ���
# - �Ƴ��
# - __?__
# - guest
sub GetUsername
{
    my ($s) = @_;
    $s =~ /^(.+?)\s+\((.*?)\)(\s+(.+))?\s*$/o;
    my ($id, $name, $extra) = ($1, $2, $4);
    if ($id eq '�ƹ���' or $id eq '�Ƴ��' or $id eq '__?__') {
	$id = $name;
    } elsif ($id eq 'guest'
	     and defined($extra) and $extra =~ /^\*\*(.+)\s*$/o) {
	$id = $1;
    }
    if (length($id) > $MAX_USERNAME) {
	$id = substr($id, 0, $MAX_USERNAME);
    }
    return $id;
}

######################################################################
# Get table name
sub GetTableName
{
    my ($b, $t) = @_;
    defined($B_KMAP{$b}) and $b = $B_KMAP{$b};
    return $t.'_'.$b;
}

######################################################################
# Generate Schema from Table Definition
sub TabDefSchema
{
    my ($td, $extra) = @_;
    my @a = ();
    foreach my $e (sort(keys(%$td))) {
	push(@a, $e.' '.$td->{$e});
    }
    defined($extra) and push(@a, $extra);
    return join(',', @a);
}

######################################################################
# Generate Name list from Table Definition
sub TabDefNames
{
    my ($td) = @_;
    return join(',', sort(keys(%$td)));
}

######################################################################
# Generate Value list from Table Definition
sub TabDefValues
{
    my ($td) = @_;
    my $n = scalar(keys(%$td));
    my @a = ();
    while ($n-- > 0) {
	push(@a, '?');
    }
    return join(',', @a);
}

######################################################################
# Generate thread hash
sub GenerateThreadHash
{
    my ($t) = @_;
    if ($t =~ /^RE:\s*(.*?)\s*$/oi) {
	$t = $1;
    }
    if (length($t) > $MAX_THREAD) {
	my $n = $MAX_THREAD;
	my $c = 0;
	while ($n > 0 and ord(substr($t, $n - 1, 1)) >= 0x80) {
	    ++$c; --$n;
	}
	$t = substr($t, 0, ($c % 2) ? $MAX_THREAD - 1 : $MAX_THREAD);
    }
    $t =~ s/\s+$//o;
    return md5_hex($t);
}

######################################################################
# Build the value list from the article
sub ArticleValues
{
    my ($a) = @_;
    my @v = ();
    my ($g, $t) = ($a->{guestkey}, $a->{title});
    length($g) > $MAX_GUESTKEY and $g = substr($g, 0, $MAX_GUESTKEY);
    length($t) > $MAX_TITLE and $t = substr($t, 0, $MAX_TITLE);
    push(@v, $a->{seq}, GetUsername($a->{author}), $a->{author}, $g,
	 $a->{datesql}, $t, GenerateThreadHash($a->{title}), $a->{body});
    return \@v;
}

######################################################################
# Fetch an old entry.
sub FetchArticleValues
{
    my ($dbh, $tn, $seq) = @_;
    my $sth = $dbh->prepare("SELECT * FROM $tn WHERE a0_seq=$seq");
    $sth->execute or return undef;
    return $sth->fetchrow_arrayref;
}

######################################################################
# Compare Values.
sub CompareArticleRows
{
    my ($r1, $r2) = @_;
    (defined($r1) and defined($r2)) or return -1;
    scalar(@$r1) == scalar(@$r2) or return 1;
    $r1->[1] eq $r2->[1] or return 1; # username
    $r1->[3] eq $r2->[3] or return 1; # guestkey
    ($r1->[4] eq '0000-00-00 00:00:00' or
     $r1->[4] eq $r2->[4]) or return 1; # datesql
    $r1->[5] eq $r2->[5] or return 1; # title
    return 0;
}

######################################################################
# Get the last seq of the board.
sub GetLastSeq
{
    my ($dbh, $tn) = @_;
    my $sth = $dbh->prepare("SELECT a0_seq FROM $tn ".
			    "ORDER BY a0_seq DESC LIMIT 1");
    $sth->execute
	or print("failed to find the last seq for $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return -1;
    my $r = $sth->fetchrow_arrayref;
    return defined($r) ? $r->[0] : 0;
}

######################################################################
# Fix article list...
sub FixBoardTable
{
    my ($dbh, $tn, $seq) = @_;
    $dbh->do("DELETE FROM $tn WHERE a0_seq>$seq")
	or print("failed to delete $seq in $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    return 1;
}

######################################################################
# Lock tables
sub LockTables
{
    my ($dbh, $lt, @tl) = @_;
    scalar(@tl) or return 0;
    my @ll;
    foreach my $tn (@tl) {
	push(@ll, $tn.' '.$lt);
    }
    $dbh->do('LOCK TABLES '.join(',', @ll))
	or print("failed to lock all tables: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    return 1;
}

######################################################################
# Unlock tables
sub UnlockTables
{
    my ($dbh) = @_;
    $dbh->do('UNLOCK TABLES')
	or print("failed to unlock tables: $DBI::errstr ($DBI::err)\n"),
	return 0;
    return 1;
}

######################################################################
# Size of a table.
sub BoardTableSize
{
    my ($dbh, $tn) = @_;
    my $sth = $dbh->prepare("SELECT COUNT(*) FROM $tn");
    $sth->execute
	or print("failed to get size of $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    my $r = $sth->fetchrow_arrayref;
    return defined($r) ? $r->[0] : 0;
}

######################################################################
# Create the table for board
sub CreateBoardTable
{
    my ($dbh, $tn) = @_;
    $dbh->do("CREATE TABLE $tn ($A_SCHEMA)")
	or print("failed to create $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    return 1;
}

######################################################################
# Drop the table
sub DropBoardTable
{
    my ($dbh, $tn) = @_;
    $dbh->do("DROP TABLE $tn")
	or print("failed to drop table $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    return 1;
}

######################################################################
# Table exists?
sub ExistsBoardTable
{
    my ($dbh, $tn) = @_;
    my $sth = $dbh->prepare('SELECT COUNT(*) FROM information_schema.tables '.
			    "WHERE table_schema='$mysql_db' ".
			    "AND table_name='$tn'");
    $sth->execute
	or print("failed to look up $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    my $r = $sth->fetchrow_arrayref;
    return defined($r) ? $r->[0] : 0;
}

######################################################################
# Trim the board
sub TrimBoardTable
{
    my ($dbh, $tn, $limit) = @_;
    my $sth = $dbh->prepare("SELECT a0_seq,a4_date FROM $tn ".
			    "WHERE DATE(a4_date)+0!=0 ".
			    "AND DATE(a4_date)+0<=DATE_SUB(DATE($S_CURDATE_KST),INTERVAL $K_MAXDAYS DAY) ".
			    "ORDER BY a0_seq DESC LIMIT 1");
    $sth->execute
	or print("failed to find the trim point for $tn: ",
		 "$DBI::errstr ($DBI::err)\n"),return 0;
    my $r = $sth->fetchrow_arrayref;
    if (defined($r) and scalar(@$r)) {
	$dbh->do("DELETE FROM $tn WHERE a0_seq<=$r->[0] ".
		 "ORDER BY a0_seq LIMIT $limit")
	    or print("failed to trim $tn: ",
		     "$DBI::errstr ($DBI::err)\n"),return 0;
    }
    return 1;
}

######################################################################
# Prepare the table for a board
sub PrepareBoardTable
{
    my ($dbh, $tn, $init) = @_;
    my $days;
    (!defined($init) or !defined($$init)) and $$init = 0;
    if ($$init) {
	if (ExistsBoardTable($dbh, $tn)) {
	    LockTables($dbh, 'WRITE', $tn) or return 0;
	    DropBoardTable($dbh, $tn);
	    UnlockTables($dbh);
	}
	CreateBoardTable($dbh, $tn) or return 0;
    } elsif (!ExistsBoardTable($dbh, $tn)) {
	$$init = 1;
	CreateBoardTable($dbh, $tn) or return 0;
    }
    LockTables($dbh, 'WRITE', $tn);
    my $cnt = BoardTableSize($dbh, $tn);
    if ($cnt > $K_MINCNT) {
	TrimBoardTable($dbh, $tn, $cnt - $K_MINCNT)
	    or UnlockTables($dbh),return 0;
    }
    return 1;
}

######################################################################
# Get the board list.
sub GetBoardList
{
    my $url = $KIDS_BLIST;
    my $bl = {};
    SetUserAgentString();
    my $rq = new HTTP::Request(GET => $url);
    my $try = $MAX_TRY;
    while ($try-- > 0) {
	my $rs = $AGENT->request($rq);
	next if (!$rs->is_success);
	foreach my $l (split(/\n/, $rs->content)) {
	    $l = utf82euckr($l);# Keep things in EUC-KR
	    if ($l =~ /\[\s*<a href=(.+?)>\s*(.+?)\s*<\/a>\s*\]/oi) {
		my $link = $1;
		if ($link =~ /Boardlist\?(.+?)=(.+?)&/) {
		    my $t = $BTYPES{$1};
		    my $name = $2;
		    if (defined($t) and $name ne 'OldAnonymous') {
			my $key = $t.'_'.$name;
			defined($bl->{$key}) or $bl->{$key} = $t;
		    }
		}
	    }
	}
	# hidden board.
	$bl->{'0_p'} = 0;
	last;
    }
    if ($try < 0) {
	print(">> request failed: $url\n");
	return undef;
    }
    return $bl;
}

######################################################################
# Get the board list from the database
sub GetBoardListFromDB
{
    my $dbh = DBI->connect($dbi_db, $dbi_user, $dbi_passwd,
			   {AutoCommit => 0})
	or print("GetBoardListFromDB: failed to connect to DB\n"),return undef;
    # list all tables
    my $sth = $dbh->prepare("SELECT table_name ".
			    "FROM information_schema.tables ".
			    "WHERE table_schema='$mysql_db'");
    $sth->execute
	or print("DropAllTables: failed to list all tables\n"),return undef;
    my $bl;
    while (my $r = $sth->fetchrow_arrayref) {
	$bl->{$r->[0]} = 0;
    }
    $dbh->disconnect;
    return $bl;
}

######################################################################
# Drop all tables
sub DropAllTables
{
    my $dbh = DBI->connect($dbi_db, $dbi_user, $dbi_passwd,
			   {AutoCommit => 0})
	or print("DropAllTables: failed to connect to DB\n"),exit(-1);
    # list all tables
    my $sth = $dbh->prepare("SELECT table_name ".
			    "FROM information_schema.tables ".
			    "WHERE table_schema='$mysql_db'");
    $sth->execute
	or print("DropAllTables: failed to list all tables\n"),exit(-1);
    my @bl;
    while (my $r = $sth->fetchrow_arrayref) {
	push(@bl, $r->[0]);
    }
    if (scalar(@bl) > 0) {
	LockTables($dbh, 'WRITE', @bl)
	    or print("DropAllTables: failed to lock db\n"),exit(-1);
	# drop all tables
	foreach my $tn (@bl) {
	    DropBoardTable($dbh, $tn);
	}
	UnlockTables($dbh)
	    or print("DropAllTables: failed to unlock db\n"),exit(-1);
    }
    $dbh->disconnect;
}

######################################################################
# Initialize the board table
# - assumes the table is empty.
sub InitBoardTable
{
    my ($b, $t, $dbh, $tn) = @_;
    my $sthI = $dbh->prepare("INSERT INTO $tn($A_NAMES) VALUES($A_VALUES)");
    my $done = 0;
    my $error = 0;
    my $days = $K_MINDAYS;
    my $d0;
    my $c = 0;
    my $u = 0;
    my $p = 'Last';
    while (1) {
	my $bp = GetBoardPage($b, $t, $p);
	(defined($bp) and scalar(@{$bp->{list}}) > 0)
	    or print("fetch failed <$b, $t, $p>\n"),
	    ++$error,$done=1,last;
	my $dx;
	foreach my $e (reverse(@{$bp->{list}})) {
	    my $a = GetArticlePage($b, $t, $e->{seq},
				   $e->{id}.' ('.$e->{name}.')',
				   $e->{title});
	    defined($a)
		or print("fetch failed <$b, $t, $e->{seq}>\n"),
		++$error,$done=1,last;

	    ++$c;		# always count
	    $dx = GetDaysFromDateSql($a->{datesql});
	    (!defined($d0) and defined($dx) and $dx != 0) and $d0 = $dx;
	    my $v = ArticleValues($a);
	    $sthI->execute(@$v) and ++$u
		or ++$error,$done=1,last;
	    if ($c >= $K_MAXCNT or
		($c >= $K_MINCNT and
		 defined($d0) and defined($dx) and
		 $dx != 0 and $d0 - $dx >= $days)) {
		$done=1,last;
	    }
	}
	# enough?
	last if ($done);
	# previous page
	$p = $bp->{list}->[0]->{seq} - 1;
	$p = 'P'.$p;
    }
    return $error > 0 ? -1 : $u;
}

######################################################################
# Update the board table
sub UpdateBoardTable
{
    my ($b, $t, $dbh, $tn, $seqDBL) = @_;
    my $ret = 0;
    my $sthI = $dbh->prepare("INSERT INTO $tn($A_NAMES) VALUES($A_VALUES)");
    my $sthU = $dbh->prepare("REPLACE INTO $tn($A_NAMES) VALUES($A_VALUES)");
    my $done = 0;
    my $u = 0;
    my $p = $seqDBL + (17 - 3);	# Just in case something got deleted
    $p = 'P'.$p;
    while (1) {
	my $bp = GetBoardPage($b, $t, $p);
	defined($bp)
	    or print("fetch failed <$b, $t, $p>\n"),
	    $done=1,last;
	if (scalar(@{$bp->{list}}) == 0) {
	    $done=1,last;
	}
	foreach my $e (@{$bp->{list}}) {
	    my $a = GetArticlePage($b, $t, $e->{seq},
				   $e->{id}.' ('.$e->{name}.')',
				   $e->{title});
	    defined($a)
		or print("fetch failed <$b, $t, $e->{seq}>\n"),
		$done=1,last;

	    my $v = ArticleValues($a);
	    my $r = FetchArticleValues($dbh, $tn, $e->{seq});
	    if (defined($r)) {
		if (CompareArticleRows($r, $v)) {
		    $sthU->execute(@$v) and ++$u
			or $done=1,last;
		}
	    } else {
		$sthI->execute(@$v) and ++$u
		    or $done=1,last;
	    }
	}
	# enough?
	last if ($done);
	# previous page
	$p = $bp->{list}->[0]->{seq} - 1;
	$p = 'N'.$p;
    }
    return $u;
}

######################################################################
# Update the board database
sub UpdateBoardDB
{
    my ($b, $t, $init) = @_;
    defined($b) and defined($t) or return -1;
    defined($init) or $init = 0;
    my $tn = GetTableName($b, $t);
    my $ret = 0;
    my $dbh;
    # open db.
    $dbh = DBI->connect($dbi_db, $dbi_user, $dbi_passwd,
			{AutoCommit => 0})
	or print("UpdateBoardDB: failed to connect to DB\n"),return -1;
    PrepareBoardTable($dbh, $tn, \$init)
	or print("UpdateBoardDB: failed to prepare $tn\n"),
	$dbh->disconnect,return -1;

    my $seqDBL = GetLastSeq($dbh, $tn);
    if ($seqDBL == 0) {
        $ret = InitBoardTable($b, $t, $dbh, $tn);
    } else {
	$ret = UpdateBoardTable($b, $t, $dbh, $tn, $seqDBL);
    }

    if ($ret < 0) {
	print("abort commit\n");
	$ret = -1;
    } else {
	if (!$dbh->commit) {
	    print("commit failed: $DBI::errstr ($DBI::err)\n");
	    $ret = -1;
	} else {
	    print($ret ? "committed $ret articles.\n" : "no change\n");
	}
    }
    UnlockTables($dbh);
    $dbh->disconnect;
    return $ret;
}

######################################################################
1;
# End.
######################################################################
