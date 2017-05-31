-- create planets
drop table if exists planets;
create table planets (id bigint auto_increment, name varchar, mass decimal);

insert into planets (name, mass) values ('Mercury', 330.2),
                                        ('Venus', 4868.5),
                                        ('Earth', 5973.6),
                                        ('Mars', 641.85),
                                        ('Jupiter', 1898600),
                                        ('Saturn', 568460),
                                        ('Uranus', 86832),
                                        ('Neptune', 102430),
                                        ('Pluto', 13.105);
