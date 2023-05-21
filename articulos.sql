CREATE TABLE articulos (id INT NOT NULL AUTO_INCREMENT, nombre VARCHAR(255) NOT NULL, descripcion TEXT, precio DECIMAL(10,2) NOT NULL, cantidad INT NOT NULL, foto longblob, PRIMARY KEY (id));

CREATE TABLE carrito_compra (id INT NOT NULL AUTO_INCREMENT, id_articulo INT NOT NULL, cantidad INT NOT NULL, PRIMARY KEY (id), FOREIGN KEY (id_articulo) REFERENCES articulos(id));