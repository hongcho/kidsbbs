<?
// Copyright (c) 2001-2010, Younghong "Hong" Cho <hongcho@sori.org>.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
//   1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//   2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//   3. Neither the name of the organization nor the names of its contributors
// may be used to endorse or promote products derived from this software
// without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
// THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// $mysql_host, $mysql_user, $mysql_passwd, $mysql_db
include('dbinfo.inc.php');

// mode
$_m = $_GET['m'];
if ($_m == NULL) {
  $_m = 'main';
 }
// board, type, table
$_b = $_GET['b'];
$_t = $_GET['t'];
if ($_t == NULL) {
  $_t = 0;
 }
$_tn = $_t.'_'.$_b;
// 0: HTML, 1: XML
$_o = $_GET['_o'];
if ($_o == NULL) {
  $_o = 0;
 }

// optional: thread id
$_id = $_GET['id'];
// optional: posting position
$_p = $_GET['p'];
// optional: username
$_u = $_GET['u'];
// start/count...
$_s = $_GET['s'];
if ($_s == NULL || $_s < 0) {
  $_s = 0;
 }
$_n = $_GET['n'];
if ($_n == NULL || $_n < 0) {
  $_n = 20;
 }

$tmap = array(0 => '보드목록',
	      1 => '작가의 마을',
	      2 => '시인의 집');
$amap = array(0 => 'Article',
	      1 => 'Article_w',
	      2 => 'Article_p');
$bmap = array('SquareMemo' => 'Square Memo',
	      'OpenLetter' => 'Square Open Letter',
	      'zDongWuHoe' => '동우회',
	      'zMeot' => '멋',
	      'zSan' => '산',
	      'zSul' => '술',
	      'zYugA' => '육아');

// get type name
function get_typename($t)
{
  global $tmap;
  return $tmap[$t];
}
// get board name
function get_boardname($b, $t)
{
  global $bmap;
  $tn = get_typename($t);
  if ($tn != NULL) {
    if ($t == 0) {
      $n = '';
    } else {
      $n = $tn.' ';
    }
    if ($bmap[$b] == NULL) {
      $n .= $b;
    } else {
      $n .= $bmap[$b];
    }
    return $n;
  } else {
    return $b;
  }
}
// get thread title
function get_threadtitle($title)
{
  if (preg_match('/^RE:\s*(.*?)\s*$/i', $title, $matches)) {
    return $matches[1];
  } else {
    return $title;
  }
}
// pad title
function pad_title($title)
{
  static $MAX_TITLE = 40;
  $n = $MAX_TITLE - strlen($title);
  while ($n-- > 0) {
    $title .= ' ';
  }
  return preg_replace('/ /', '&nbsp;', $title);
}
// trim broken korean
function trim_broken_korean($s, $M)
{
  $len = strlen($s);
  if ($len < $M) {
    $M = $len;
  }
  $n = $M;
  $c = 0;
  while ($n > 0 and ord(substr($s, $n - 1, 1)) >= 0x80) {
    ++$c; --$n;
  }
  if ($c % 2) {
    return substr($s, 0, $M - 1). ' ';
  } else {
    return  substr($s, 0, $M);
  }
}
// remove href
function remove_kids_href($s)
{
  return preg_replace('/<a\s+href="(.*?)"\s+target=_blank>.*?<\/a>/i',
		      '$1', $s);
}
// remove kids html
function remove_kids_html($s)
{
  $s = remove_kids_href($s);
  $s = preg_replace('/<br\/>/i', "\n", $s);
  $s = preg_replace('/&nbsp;/i', ' ', $s);
  $s = preg_replace('/&lt;/i', '<', $s);
  return $s;
}
// get summary
function get_summary($s)
{
  static $MAX_SUMMARY = 50;
  $s = remove_kids_html($s);
  $s = preg_replace('/\n+/i', ' ', $s);
  $s = preg_replace('/\s+/i', ' ', $s);
  $s = preg_replace('/^\s+/i', '', $s);
  return trim_broken_korean($s, $MAX_SUMMARY);
}

// convert SQL date/time to RFC 822
function to_date822($d)
{
  // 2010-09-26 08:15:31
  // -> 26 Sep 2010 08:15:31
  static $mmap = array('Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
		       'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec');
  if (preg_match('/^\s*(\d{4})-(\d{2})-(\d{2})\s+(\d{2}:\d{2}:\d{2})\s*$/',
		 $d, $matches)) {
    return $matches[3].' '.$mmap[$matches[2]-1].' '.$matches[1].' '.
      $matches[4];
  } else {
    return '';
  }
}
// linkify...
function linkify_string($s)
{
  return preg_replace('/(^|(&nbsp;)+)((https?|mms|ftp|mailto|rtsp):\/\/.+?(?=(&nbsp;|$)))/i',
		      '$1<A href="$3">$3</A>', $s);
}

// connect to db
function db_open()
{
  global $mysql_host, $mysql_user, $mysql_passwd, $mysql_db;
  mysql_connect($mysql_host, $mysql_user, $mysql_passwd)
    or die('unabled to connect to db');
  @mysql_select_db($mysql_db) or die('unabled to select db');
}
// disconnect from db
function db_close()
{
  mysql_close();
}
// db query string
function get_dbquery($m)
{
  global $mysql_db;
  global $_tn,$_id,$_p,$_u;
  $tn = mysql_real_escape_string($_tn);
  switch ($m) {
  case 'rss':
    return "SELECT a0_seq,a1_username,a2_author,a4_date,a5_title,a7_body ".
      "FROM $tn ".
      "WHERE a4_date+0>CONVERT_TZ(NOW(),'US/Mountain','Asia/Seoul')-60000 ".
      "ORDER BY a0_seq DESC";
    break;
  case 'rss1':
    return "SELECT a0_seq,a1_username,a2_author,a4_date,a5_title,a7_body ".
      "FROM $tn ORDER BY a0_seq DESC LIMIT 1";
    break;
  case 'list':
    return "SELECT a0_seq,a1_username,a2_author,a4_date,a5_title,a6_thread,a7_body ".
      "FROM $tn ORDER BY a0_seq DESC";
  case 'tlist':
    return "SELECT a0_seq,a1_username,a4_date,a5_title,a6_thread,COUNT(*) AS cnt,a7_body ".
      "FROM (SELECT * FROM $tn ORDER BY a0_seq DESC) AS t ".
      "GROUP BY a6_thread ORDER BY a0_seq DESC";
  case 'thread':
    $id = mysql_real_escape_string($_id);
    return "SELECT a0_seq,a1_username,a4_date,a5_title,a7_body FROM $tn ".
      "WHERE a6_thread='$id' ORDER BY a0_seq DESC";
  case 'user':
    $u = mysql_real_escape_string($_u);
    return "SELECT a0_seq,a1_username,a4_date,a5_title,a7_body FROM $tn ".
      "WHERE a1_username='$u' ORDER BY a0_seq DESC";
  case 'view':
    $p = mysql_real_escape_string($_p);
    return "SELECT a0_seq,a1_username,a2_author,a4_date,a5_title,a6_thread,a7_body ".
      "FROM $tn WHERE a0_seq=$p";
  case 'main':
  default:
    return "SELECT table_name FROM information_schema.tables ".
      "WHERE table_schema='$mysql_db' ORDER BY table_name";
  }
}

// header
function display_header($b, $t, $title, $nt, $s, $n, $type)
{
  global $_o;
  switch ($_o) {
  case 0:
    echo "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/1999/REC-html401-19991224/loose.dtd\">\n";
    echo "<HTML>\n<HEAD>\n";
    echo "<LINK rel=\"SHORTCUT ICON\" href=\"/kids/favicon.ico\">\n";
    echo "<META http-equiv=\"Content-Type\" content=\"text/html; charset=euc-kr\">\n";
    echo "<TITLE>$title</TITLE>\n";
    echo "<BASE target=\"_top\">\n";
    echo "<LINK rel=\"stylesheet\" href=\"kids.css\" type=\"text/css\">\n";
    echo "<SCRIPT SRC=\"script.js\"></SCRIPT>\n";
    echo "</HEAD>\n<BODY text=\"#000000\" bgcolor=\"#FFFFFF\">\n";
    if ($type != 'main') {
      echo "<TABLE cellpadding=\"1\" cellspacing=\"0\" border=\"1\">\n";
    }
    break;
  case 1:
    header('Content-Type: text/xml');
    echo "<?xml version=\"1.0\" encoding=\"euc-kr\"?>\n";
    echo "<KIDSBBS>\n";
    switch ($type) {
    case 'list':
    case 'tlist':
    case 'thread':
    case 'user':
    case 'view':
      echo "<ITEMS type=\"$type\">\n";
      echo "<BOARD>$t".'_'."$b</BOARD>\n";
      echo "<TITLE><![CDATA[$title]]></TITLE>\n";
      echo "<START>$s</START>\n";
      echo "<COUNT>$n</COUNT>\n";
      echo "<TOTALCOUNT>$nt</TOTALCOUNT>\n";
      break;
      break;
    }
    break;
  default:
    die("unknown output format: $o");
  }
}
// footer
function display_footer($type)
{
  global $_o;
  switch ($_o) {
  case 0:
    if ($type != 'main') {
      echo "\n</TABLE>";
    }
    echo "</BODY>\n</HTML>\n";
    break;
  case 1:
    if ($type != 'main') {
      echo "</ITEMS>\n";
    }
    echo "</KIDSBBS>\n";
    break;
  default:
    die("unknown output format: $o");
  }
}

// generate xml main item
function gen_xml_mainitem($b, $t, $nt, $np)
{
  echo "<ITEM>\n";
  echo "<BOARD>$t".'_'."$b</BOARD>\n";
  echo "<TITLE><![CDATA[".get_boardname($b, $t)."]]></TITLE>\n";
  echo "<THRCOUNT>$nt</THRCOUNT>\n";
  echo "<POSTCOUNT>$np</POSTCOUNT>\n";
  echo "</ITEM>\n";
}
// generate xml item
function gen_xml_item($thread, $cnt,
		      $seq, $user, $author, $date, $title, $desc)
{
  if ($cnt == NULL) {
    $cnt = 1;
  }
  echo "<ITEM>\n";
  if ($thread != NULL) {
    echo "<THREAD><![CDATA[$thread]]></THREAD>\n";
  }
  echo "<COUNT>$cnt</COUNT>\n";
  echo "<TITLE><![CDATA[$title]]></TITLE>\n";
  echo "<SEQ>$seq</SEQ>\n";
  echo "<DATE tz=\"KST\">$date</DATE>\n";
  echo "<USER><![CDATA[$user]]></USER>\n";
  if ($author != NULL) {
    echo "<AUTHOR><![CDATA[$author]]></AUTHOR>\n";
  }
  echo "<DESCRIPTION><![CDATA[$desc]]></DESCRIPTION>\n";
  echo "</ITEM>\n";
}
// generate list
function gen_list(&$qr)
{
  global $_b,$_t,$_o,$_s,$_n;
  $nr = mysql_num_rows($qr);
  $s = $_s < $nr ? $_s : $nr;
  $n = $s + $_n <= $nr ? $_n : $nr - $s;

  display_header($_b, $_t, get_boardname($_b, $_t)." ($n threads)", $nr,
		 $s, $n, 'list');
  for ($i = $s; $i < $s + $n; ++$i) {
    $seq = mysql_result($qr, $i, 'a0_seq');
    $username = mysql_result($qr, $i, 'a1_username');
    $author = mysql_result($qr, 0, 'a2_author');
    $date = mysql_result($qr, $i, 'a4_date');
    $title = mysql_result($qr, $i, 'a5_title');
    $thread = mysql_result($qr, $i, 'a6_thread');
    $body = mysql_result($qr, $i, 'a7_body');

    switch ($_o) {
    case 0:
      $title = pad_title($title);
      $body = get_summary($body);
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><FONT size=\"4pt\" style=\"font-family:monospace\"><A href=\"?m=view&b=$b&t=$t&p=$seq\">$title</A>&nbsp;</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;</FONT>\n";
      echo "</TABLE>\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD width=\"60\"><FONT size=\"2pt\"><A href=\"?m=user&b=$_b&t=$_t&u=$username\">$username</A>&nbsp;</FONT>\n";
      echo "<TD><FONT size=\"2pt\" style=\"font-family:monospace\">$body</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$date</A></FONT>\n";
      echo "</TABLE>\n";
      echo "</TABLE>\n";
      break;
    case 1:
      $body = remove_kids_html($body);
      $body = trim_broken_korean($body, strlen($body));
      gen_xml_item($thread, 1, $seq, $username, $author, $date, $title, $body);
      break;
    }
  }
  display_footer('list');
}
// generate threaded list
function gen_tlist(&$qr)
{
  global $_b,$_t,$_tn,$_o,$_s,$_n;
  $nr = mysql_num_rows($qr);
  $s = $_s < $nr ? $_s : $nr;
  $n = $s + $_n <= $nr ? $_n : $nr - $s;

  display_header($_b, $_t, get_boardname($_b, $_t)." ($n threads)", $nr,
		 $s, $n, 'tlist');
  for ($i = $s; $i < $s + $n; ++$i) {
    $seq = mysql_result($qr, $i, 'a0_seq');
    $username = mysql_result($qr, $i, 'a1_username');
    $date = mysql_result($qr, $i, 'a4_date');
    $title = get_threadtitle(mysql_result($qr, $i, 'a5_title'));
    $thread = mysql_result($qr, $i, 'a6_thread');
    $cnt = mysql_result($qr, $i, 'cnt');
    $body = mysql_result($qr, $i, 'a7_body');

    $body = get_summary($body);

    switch ($_o) {
    case 0:
      if ($cnt > 1) {
	$url = "?m=thread&b=$_b&t=$_t&id=$thread";
      } else {
	$url = "?m=view&b=$_b&t=$_t&p=$seq";
      }
      $title = pad_title($title);
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><FONT size=\"4pt\" style=\"font-family:monospace\"><A href=\"$url\">$title</A>&nbsp;</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;";
      if ($cnt > 1) {
	echo "<A href=\"$url\">($cnt)&gt;&gt;</A>";
      }
      echo "</FONT>\n";
      echo "</TABLE>\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD width=\"60\"><FONT size=\"2pt\"><A href=\"?m=user&b=$_b&t=$_t&u=$username\">$username</A>&nbsp;</FONT>\n";
      echo "<TD><FONT size=\"2pt\" style=\"font-family:monospace\">$body</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$date</A></FONT>\n";
      echo "</TABLE>\n";
      echo "</TABLE>\n";
      break;
    case 1:
      gen_xml_item($thread, $cnt, $seq, $username, NULL, $date, $title, $body);
      break;
    }
  }
  display_footer('tlist');
}
// generate thread
function gen_thread(&$qr)
{
  global $_b,$_t,$_o,$_s,$_n;
  $nr = mysql_num_rows($qr);
  $thr_title = get_threadtitle(mysql_result($qr, 0, 'a5_title'));
  $s = $_s < $nr ? $_s : $nr;
  $n = $s + $_n <= $nr ? $_n : $nr - $s;

  display_header($_b, $_t, "$thr_title ($n) in ".get_boardname($_b, $_t), $nr,
		 $s, $n, 'thread');
  for ($i = $s; $i < $s + $n; ++$i) {
    $seq = mysql_result($qr, $i, 'a0_seq');
    $username = mysql_result($qr, $i, 'a1_username');
    $date = mysql_result($qr, $i, 'a4_date');
    $title = mysql_result($qr, $i, 'a5_title');
    $body = mysql_result($qr, $i, 'a7_body');

    $body = get_summary($body);

    switch ($_o) {
    case 0:
      $title = pad_title($title);
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><FONT size=\"4pt\" style=\"font-family:monospace\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$title</A>&nbsp;</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;";
      echo "</FONT>\n";
      echo "</TABLE>\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD width=\"60\"><FONT size=\"2pt\"><A href=\"?m=user&b=$_b&t=$_t&u=$username\">$username</A>&nbsp;</FONT>\n";
      echo "<TD><FONT size=\"2pt\" style=\"font-family:monospace\">$body</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$date</A></FONT>\n";
      echo "</TABLE>\n";
      echo "</TABLE>\n";
      break;
    case 1:
      gen_xml_item(NULL, NULL, $seq, $username, NULL, $date, $title, $body);
      break;
    }
  }
  display_footer('thread');
}
// generate user post list
function gen_user(&$qr)
{
  global $_b,$_t,$_u,$_o,$_s,$_n;
  $nr = mysql_num_rows($qr);
  $s = $_s < $nr ? $_s : $nr;
  $n = $s + $_n <= $nr ? $_n : $nr - $s;

  display_header($_b, $_t, "$_u in ".get_boardname($_b, $_t), $nr,
		 $s, $n, 'user');
  for ($i = $s; $i < $s + $n; ++$i) {
    $seq = mysql_result($qr, $i, 'a0_seq');
    $username = mysql_result($qr, $i, 'a1_username');
    $date = mysql_result($qr, $i, 'a4_date');
    $title = mysql_result($qr, $i, 'a5_title');
    $body = mysql_result($qr, $i, 'a7_body');

    $body = get_summary($body);

    switch ($_o) {
    case 0:
      $title = pad_title($title);
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD><FONT size=\"4pt\" style=\"font-family:monospace\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$title</A>&nbsp;</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;";
      echo "</FONT>\n";
      echo "</TABLE>\n";
      echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n";
      echo "<TR><TD width=\"60\"><FONT size=\"2pt\"><A href=\"?m=user&b=$_b&t=$_t&u=$username\">$username</A>&nbsp;</FONT>\n";
      echo "<TD><FONT size=\"2pt\" style=\"font-family:monospace\">$body</FONT>\n";
      echo "<TD align=\"right\"><FONT size=\"2pt\"><A href=\"?m=view&b=$_b&t=$_t&p=$seq\">$date</A></FONT>\n";
      echo "</TABLE>\n";
      echo "</TABLE>\n";
      break;
    case 1:
      gen_xml_item(NULL, NULL, $seq, $username, NULL, $date, $title, $body);
      break;
    }
  }
  display_footer('user');
}
// generate post view
function gen_view(&$qr)
{
  global $_b,$_t,$_p,$_o;
  $seq = mysql_result($qr, 0, 'a0_seq');
  $username = mysql_result($qr, 0, 'a1_username');
  $author = mysql_result($qr, 0, 'a2_author');
  $date = mysql_result($qr, 0, 'a4_date');
  $title = mysql_result($qr, 0, 'a5_title');
  $thread = mysql_result($qr, 0, 'a6_thread');
  $body = mysql_result($qr, 0, 'a7_body');

  display_header($_b, $_t, "$title by $username in ".get_boardname($_b, $_t),
		 1, 0, 1, 'view');

  switch ($_o) {
  case 0:
    $title = pad_title($title);
    $body1 = '';
    foreach (preg_split('/<br\/>/', $body) as $l) {
      $body1 .= linkify_string($l).'<br/>';
    }
    echo "<TR><TD><TABLE width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">";
    echo "<TR><TD><FONT size=\"4pt\" style=\"font-family:monospace\"><A href=\"?m=thread&b=$_b&t=$_t&id=$thread\">$title</A>&nbsp;</FONT>";
    echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;$seq in [",
      get_boardname($_b, $_t), "]</FONT>";
    echo "<TR><TD><FONT size=\"2pt\"><A href=\"?m=user&b=$_b&t=$_t&u=$username\">$author</A>&nbsp;</FONT>";
    echo "<TD align=\"right\"><FONT size=\"2pt\">&nbsp;$date\n</FONT>";
    echo "\n</TABLE>\n";
    echo "<TR><TD><FONT size=\"2pt\" style=\"font-family:monospace\">$body1</FONT>\n";
    break;
  case 1:
    $body = remove_kids_html($body);
    $body = trim_broken_korean($body, strlen($body));
    gen_xml_item($thread, 1, $seq, $username, $author, $date, $title, $body);
   break;
  }
  display_footer('view');
}
// generate main
function gen_main(&$qr)
{
  global $_o;
  $nr = mysql_num_rows($qr);
  display_header(NULL, NULL, "키즈 쓰레드 보드 목록 ($nr)", $nr,
		 0, $nr, 'main');
  $nc = 4;
  $cnt = 0;
  $last_t = -1;
  for ($i = 0; $i < $nr; ++$i, ++$cnt) {
    $tn = mysql_result($qr, $i, 'table_name');
    if (preg_match('/^(\d+)_(\S+)$/', $tn, $matches)) {
      $t = $matches[1];
      $b = $matches[2];
    } else {
      continue;
    }

    $tnE = mysql_real_escape_string($tn);
    $rx = mysql_query(sprintf("SELECT COUNT(*) AS cnt FROM %s", $tnE));
    $n_posts = mysql_result($rx, 0, 'cnt');
    $rx = mysql_query(sprintf("SELECT COUNT(*) AS cnt FROM (SELECT a0_seq FROM %s GROUP BY a6_thread) AS t", $tnE));
    $n_threads = mysql_result($rx, 0, 'cnt');

    switch ($_o) {
    case 0:
      if ($last_t != $t) {
	if ($last_t >= 0) {
	  $n = ($nc - $cnt % $nc) % $nc;
	  for ($j = 0; $j < $n; ++$j) {
	    echo "<TD>&nbsp;\n";
	  }
	  echo "</TABLE>\n";
	}
	$last_t = $t;
	echo "<H1>".get_typename($last_t)." <FONT size=\"-1pt\">(쓰레드 갯수|글 갯수)</FONT></H1>\n";
	echo "<TABLE cellpadding=\"5\" cellspacing=\"0\" border=\"1\">\n";
	$cnt = 0;
      }
      if ($cnt % $nc == 0) {
	echo '<TR>';
      }
      echo "<TD><A href=\"?m=tlist&b=$b&t=$t\">", get_boardname($b, $t),
	"</A>&nbsp;<FONT size=\"2\">($n_threads|$n_posts)</FONT>\n";
      break;
    case 1:
      if ($last_t != $t) {
	if ($last_t >= 0) {
	  echo "</ITEMS>\n";
	}
	$last_t = $t;
	echo "<ITEMS type=\"main\">\n";
	echo "<TITLE><![CDATA[".get_typename($last_t)."]]></TITLE>\n";
	$cnt = 0;
      }
      gen_xml_mainitem($b, $t, $n_threads, $n_posts);
      break;
    }
  }
  switch ($_o) {
  case 0:
    $n = ($nc - $cnt % $nc) % $nc;
    for ($j = 0; $j < $n; ++$j) {
      echo '<TD>&nbsp;';
    }
    break;
  case 1:
    echo "</ITEMS>\n";
    break;
  }
  display_footer('main');
}
// generate output
function gen_output()
{
  global $_m;
  db_open();
  $q = get_dbquery($_m);
  $qr = mysql_query($q);
  switch ($_m) {
  case 'list':
    gen_list($qr);
    break;
  case 'tlist':
    gen_tlist($qr);
    break;
  case 'thread':
    gen_thread($qr);
    break;
  case 'user':
    gen_user($qr);
    break;
  case 'view':
    gen_view($qr);
    break;
  case 'main':
  default:
    gen_main($qr);
    break;
  }
  db_close();
}

// generate rss main
function gen_rss_main()
{
  $q = get_dbquery('main');
  $qr = mysql_query($q);
  $nr = mysql_num_rows($qr);
  display_header(NULL, NULL, "키즈 RSS 목록 ($nr)", $nr, 0, $nr, 'main');
  $nc = 4;
  $cnt = 0;
  $last_t = -1;
  for ($i = 0; $i < $nr; ++$i, ++$cnt) {
    $tn = mysql_result($qr, $i, 'table_name');
    if (preg_match('/^(\d+)_(\S+)$/', $tn, $matches)) {
      $t = $matches[1];
      $b = $matches[2];
    } else {
      continue;
    }

    if ($last_t != $t) {
      if ($last_t >= 0) {
	$n = ($nc - $cnt % $nc) % $nc;
	for ($j = 0; $j < $n; ++$j) {
	  echo "<TD>&nbsp;\n";
	}
	echo "</TABLE>\n";
      }
      $last_t = $t;
      echo "<H1>".get_typename($last_t)." RSS</H1>\n";
      echo "<TABLE cellpadding=\"5\" cellspacing=\"0\" border=\"1\">\n";
      $cnt = 0;
    }
    if ($cnt % $nc == 0) {
      echo '<TR>';
    }
    echo "<TD><A href=\"?b=$b&t=$t\">", get_boardname($b, $t),
      "</A>\n";
  }
  $n = ($nc - $cnt % $nc) % $nc;
  for ($j = 0; $j < $n; ++$j) {
    echo '<TD>&nbsp;';
  }
  display_footer('main');
}
// generate rss feed
function gen_rss_feed($b, $t)
{
  global $amap;
  $kids_host = 'kids.kornet.net';
  $copyright = 'Copyright (c) 1991-2010, KIDS BBS';
  $contact = 'kids@sori.org';
  $generator = 'Hong\'s KIDS BBS RSS Generator';

  $q = get_dbquery('rss');
  $qr = mysql_query($q);
  $nr = mysql_num_rows($qr);
  if ($nr < 1) {
    // at least one.
    $q = get_dbquery('rss1');
    $qr = mysql_query($q);
    $nr = mysql_num_rows($qr);
  }

  $title = get_boardname($b, $t);
  $pubDate = date("d M Y H:i:s T");
  $buildDate = to_date822(mysql_result($qr, 0, 'a4_date'));

  header('Content-Type: text/xml');
  echo "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n";
  echo "<rss version=\"2.0\">";
  echo "<channel>\n";
  echo "<title><![CDATA[KIDS: $title]]></title>\n";
  echo "<description><![CDATA[KIDS BBS $title Board]]></description>\n";
  echo "<language>ko</language>\n";
  echo "<copyright>$copyright</copyright>\n";
  echo "<managingEditor>$contact</managingEditor>\n";
  echo "<webMaster>$contact</webMaster>\n";
  echo "<pubDate>$pubDate</pubDate>\n";
  echo "<lastBuildDate>$buildDate KST</lastBuildDate>\n";
  echo "<category>KIDS BBS</category>\n";
  echo "<generator>$generator</generator>\n";
  echo "<docs>http://www.rssboard.org/rss-spcification</docs>\n";
  echo "<ttl>120</ttl>\n";

  for ($i = 0; $i < $nr; ++$i) {
    $seq = mysql_result($qr, $i, 'a0_seq');
    $username = mysql_result($qr, $i, 'a1_username');
    $author = mysql_result($qr, $i, 'a2_author');
    $date = mysql_result($qr, $i, 'a4_date');
    $title = mysql_result($qr, $i, 'a5_title');
    $body = mysql_result($qr, $i, 'a7_body');

    $url = "http://$kids_host/cgi-bin/Boardlist?$amap[$t]=$b&Num=$seq";
    $guid = $url.';'.md5($date);

    $date = to_date822($date);

    $desc = '';
    foreach (preg_split('/<br\/>/', $body) as $l) {
      $desc .= linkify_string($l).'<br/>';
    }
    $desc = '<div style="font-family:monospace">'.$desc.'</div>';

    echo "<item>\n";
    echo "<title><![CDATA[$title]]></title>\n";
    echo "<link><![CDATA[$url]]></link>\n";
    echo "<guid><![CDATA[$guid]]></guid>\n";
    echo "<description><![CDATA[$desc]]></description>\n";
    echo "<author><![CDATA[$author]]></author>\n";
    echo "<category>KIDS</category>\n";
    echo "<pubDate>$date KST</pubDate>\n";
    echo "</item>\n";
  }

  echo "</channel>\n";
  echo "</rss>\n";
}
# gen rss
function gen_rss()
{
  global $_b,$_t,$_o;
  db_open();
  if ($_b != NULL && $_t != NULL) {
    // always an XML
    $_o = 1;
    gen_rss_feed($_b, $_t);
  } else {
    // always an HTML
    $_o = 0;
    gen_rss_main();
  }
  db_close();
}
?>
