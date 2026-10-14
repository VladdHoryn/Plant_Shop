INSERT INTO categories (name, description)
VALUES ('Indoor', 'Houseplants that thrive inside'),
       ('Succulents & Cacti', 'Drought-tolerant plants that store water'),
       ('Outdoor', 'Garden and balcony plants'),
       ('Herbs', 'Edible and aromatic herbs');

-- Plant ids 1..10 are relied on by inventory-service V2 seed data
INSERT INTO plants (name, latin_name, description, price, category_id, care_level, light_requirement, pot_size_cm, image_url)
VALUES ('Monstera Deliciosa', 'Monstera deliciosa', 'Iconic split-leaf plant with large glossy leaves.', 34.99,
        (SELECT id FROM categories WHERE name = 'Indoor'), 'EASY', 'BRIGHT_INDIRECT', 17, NULL),
       ('Fiddle Leaf Fig', 'Ficus lyrata', 'Statement tree with violin-shaped leaves.', 49.90,
        (SELECT id FROM categories WHERE name = 'Indoor'), 'HARD', 'BRIGHT_INDIRECT', 21, NULL),
       ('Snake Plant', 'Dracaena trifasciata', 'Nearly indestructible, purifies indoor air.', 22.50,
        (SELECT id FROM categories WHERE name = 'Indoor'), 'EASY', 'LOW', 14, NULL),
       ('Peace Lily', 'Spathiphyllum wallisii', 'Elegant white blooms, tells you when it is thirsty.', 19.99,
        (SELECT id FROM categories WHERE name = 'Indoor'), 'EASY', 'LOW', 12, NULL),
       ('Calathea Orbifolia', 'Goeppertia orbifolia', 'Striped round leaves that fold at night.', 29.00,
        (SELECT id FROM categories WHERE name = 'Indoor'), 'HARD', 'MEDIUM', 15, NULL),
       ('Aloe Vera', 'Aloe barbadensis', 'Medicinal succulent with soothing gel.', 12.99,
        (SELECT id FROM categories WHERE name = 'Succulents & Cacti'), 'EASY', 'DIRECT_SUN', 10, NULL),
       ('Golden Barrel Cactus', 'Echinocactus grusonii', 'Round cactus with golden spines.', 17.49,
        (SELECT id FROM categories WHERE name = 'Succulents & Cacti'), 'EASY', 'DIRECT_SUN', 12, NULL),
       ('Japanese Maple', 'Acer palmatum', 'Ornamental tree with vivid red autumn foliage.', 89.00,
        (SELECT id FROM categories WHERE name = 'Outdoor'), 'MEDIUM', 'MEDIUM', 30, NULL),
       ('English Lavender', 'Lavandula angustifolia', 'Fragrant purple flowers loved by bees.', 9.99,
        (SELECT id FROM categories WHERE name = 'Outdoor'), 'MEDIUM', 'DIRECT_SUN', 13, NULL),
       ('Sweet Basil', 'Ocimum basilicum', 'Classic culinary herb for the kitchen windowsill.', 4.99,
        (SELECT id FROM categories WHERE name = 'Herbs'), 'EASY', 'DIRECT_SUN', 9, NULL);
