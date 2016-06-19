#!/bin/sh
# Not general-purpose; that would be a little more work.
pandoc -f markdown -t plain | perl -E '
	while(<>) {
		chomp;
		s/\\/\\\\/g;
		s/"/\\"/g;
		say "\"$_\",";
	}
'
