package servicio_json;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.QueryParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.Response;

import java.math.BigDecimal;
import java.sql.*;
import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.*;

// la URL del servicio web es http://localhost:8080/Servicio/rest/ws
// donde:
//	"Servicio" es el dominio del servicio web (es decir, el nombre de archivo Servicio.war)
//	"rest" se define en la etiqueta <url-pattern> de <servlet-mapping> en el archivo WEB-INF\web.xml
//	"ws" se define en la siguiente anotación @Path de la clase Servicio

@Path("ws")
public class Servicio {
  static DataSource pool = null;
  static {
    try {
      Context ctx = new InitialContext();
      pool = (DataSource) ctx.lookup("java:comp/env/jdbc/datasource_Servicio");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  static Gson j = new GsonBuilder().registerTypeAdapter(byte[].class, new AdaptadorGsonBase64())
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").create();

  @POST
  @Path("alta_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response altaArticulo(String json) throws Exception {
    ParamAltaArticulo p = (ParamAltaArticulo) j.fromJson(json, ParamAltaArticulo.class);
    Articulo articulo = p.articulo;

    Connection conexion = pool.getConnection();

    if (articulo.nombre == null || articulo.nombre.equals(""))
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar el nombre del articulo"))).build();

    if (articulo.precio.compareTo(BigDecimal.ZERO) <= 0)
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar un precio valido"))).build();

    if (articulo.cantidad <= 0)
      return Response.status(400).entity(j.toJson(new Error("Se debe ingresar una cantidad valida"))).build();

    try {
      conexion.setAutoCommit(false);

      PreparedStatement stmt = conexion.prepareStatement(
          "INSERT INTO articulos (nombre, descripcion, precio, cantidad, foto) VALUES (?, ?, ?, ?, ?)");

      try {
        stmt.setString(1, articulo.nombre);
        stmt.setString(2, articulo.descripcion);
        stmt.setBigDecimal(3, articulo.precio);
        stmt.setInt(4, articulo.cantidad);
        stmt.setBytes(5, articulo.foto);
        stmt.executeUpdate();
      } finally {
        stmt.close();
      }
      conexion.commit();
      return Response.status(200).entity(j.toJson("Articulo registrado")).build();
    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @POST
  @Path("consulta_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response busquedaArticulos(String json) throws Exception {
    ParamConsultaArticulo p = (ParamConsultaArticulo) j.fromJson(json, ParamConsultaArticulo.class);
    String busqueda = p.busqueda;

    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);
      String query = "SELECT nombre, descripcion, precio, foto FROM articulos WHERE nombre LIKE ? OR descripcion LIKE ?";
      PreparedStatement statement = conexion.prepareStatement(query);
      statement.setString(1, "%" + busqueda + "%");
      statement.setString(2, "%" + busqueda + "%");
      ResultSet rs = statement.executeQuery();

      List<Articulo> articulos = new ArrayList<>();
      while (rs.next()) {
        Articulo articulo = new Articulo();
        articulo.nombre = (rs.getString("nombre"));
        articulo.descripcion = (rs.getString("descripcion"));
        articulo.precio = (rs.getBigDecimal("precio"));
        articulo.foto = (rs.getBytes("foto"));
        articulos.add(articulo);
      }
      conexion.commit();
      if (articulos.isEmpty()) {
        return Response.status(200).entity("No se encontraron resultados para la búsqueda").build();
      } else {
        return Response.status(200).entity(j.toJson(articulos)).build();
      }
    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @POST
  @Path("comprar_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response compraArticulos(String json) throws Exception {
    ParamAgregarArticulo p = (ParamAgregarArticulo) j.fromJson(json, ParamAgregarArticulo.class);
    String nombre = p.nombre;
    String descripcion = p.descripcion;
    int cantidad = p.cantidad;

    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);

      // Buscar el artículo por nombre y descripción
      String query = "SELECT id, cantidad FROM articulos WHERE nombre = ? AND descripcion = ?";
      PreparedStatement statement = conexion.prepareStatement(query);
      statement.setString(1, nombre);
      statement.setString(2, descripcion);
      ResultSet rs = statement.executeQuery();

      // Si no se encuentra el artículo, regresar un mensaje de error
      if (!rs.next()) {
        return Response.status(404).entity(j.toJson(new Error("No se encontró el artículo solicitado"))).build();
      }

      // Obtener la cantidad disponible del artículo en la base de datos
      int idArticulo = rs.getInt("id");
      int cantidadEnBaseDatos = rs.getInt("cantidad");

      // Obtener la cantidad solicitada del artículo en el carrito de compras
      query = "SELECT cantidad FROM carrito_compra WHERE id_articulo = ?";
      statement = conexion.prepareStatement(query);
      statement.setInt(1, idArticulo);
      rs = statement.executeQuery();
      int cantidadEnCarrito = 0;
      if (rs.next()) {
        cantidadEnCarrito = rs.getInt("cantidad");
      }

      // Calcular la cantidad total del artículo
      int cantidadNueva = cantidad - cantidadEnCarrito;

      // Si la cantidad solicitada es mayor que la cantidad total,
      // regresar un mensaje de error
      if (cantidadNueva > cantidadEnBaseDatos) {
        return Response.status(404)
            .entity(j.toJson(new Error("No hay suficiente stock para el artículo solicitado")))
            .build();
      }

      // Actualizar la cantidad en el carrito de compras
      if (cantidadEnCarrito == 0) {
        // Si no había ninguna cantidad en el carrito de compras, insertar un nuevo
        // registro
        query = "INSERT INTO carrito_compra (id_articulo, cantidad) VALUES (?, ?)";
        statement = conexion.prepareStatement(query);
        statement.setInt(1, idArticulo);
        statement.setInt(2, cantidad);
        statement.executeUpdate();
      } else {
        // Si ya había una cantidad en el carrito de compras, actualizarla
        query = "UPDATE carrito_compra SET cantidad = ? WHERE id_articulo = ?";
        statement = conexion.prepareStatement(query);
        statement.setInt(1, cantidad);
        statement.setInt(2, idArticulo);
        statement.executeUpdate();
      }

      // Actualizar la cantidad disponible en la tabla de articulos
      int cantidadDiferencia = cantidad - cantidadEnCarrito;
      query = "UPDATE articulos SET cantidad = cantidad - ? WHERE id = ?";
      statement = conexion.prepareStatement(query);
      statement.setInt(1, cantidadDiferencia);
      statement.setInt(2, idArticulo);
      statement.executeUpdate();

      conexion.commit();
      return Response.status(200).entity("Artículo agregado al carrito de compras").build();

    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @GET
  @Path("mostrar_carrito_compras")
  @Produces(MediaType.APPLICATION_JSON)
  public Response mostrarCarritoCompras() throws Exception {
    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);

      String query = "SELECT a.nombre, a.precio, a.foto, cc.cantidad "
          + "FROM articulos a "
          + "JOIN carrito_compra cc ON a.id = cc.id_articulo";

      PreparedStatement statement = conexion.prepareStatement(query);
      ResultSet rs = statement.executeQuery();

      List<ArticuloCarrito> articulosCarrito = new ArrayList<>();
      while (rs.next()) {
        ArticuloCarrito articuloCarrito = new ArticuloCarrito();
        articuloCarrito.nombre = rs.getString("nombre");
        articuloCarrito.precio = rs.getBigDecimal("precio");
        articuloCarrito.foto = rs.getBytes("foto");
        articuloCarrito.cantidad = rs.getInt("cantidad");
        articulosCarrito.add(articuloCarrito);
      }
      return Response.status(200).entity(j.toJson(articulosCarrito)).build();
    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @POST
  @Path("eliminar_articulo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response eliminarArticulo(String json) throws Exception {
    ParamEliminarArticulo p = (ParamEliminarArticulo) j.fromJson(json, ParamEliminarArticulo.class);
    String nombre = p.nombre;
    String descripcion = p.descripcion;

    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);
      // Buscar el artículo en la tabla de artículos
      String query = "SELECT id, cantidad FROM articulos WHERE nombre = ? AND descripcion = ?";
      PreparedStatement statement = conexion.prepareStatement(query);
      statement.setString(1, nombre);
      statement.setString(2, descripcion);
      ResultSet rs = statement.executeQuery();

      // Si no se encuentra el artículo, regresar un mensaje de error
      if (!rs.next()) {
        return Response.status(404).entity(j.toJson(new Error("No se encontró el artículo solicitado"))).build();
      }

      // Obtener la cantidad del artículo en la base de datos
      int cantidadEnBaseDatos = rs.getInt("cantidad");

      // Obtener el ID del artículo en la base de datos
      int idArticulo = rs.getInt("id");

      // Buscar el artículo en la tabla de carrito_compra
      query = "SELECT cantidad FROM carrito_compra WHERE id_articulo = ?";
      statement = conexion.prepareStatement(query);
      statement.setInt(1, idArticulo);
      rs = statement.executeQuery();

      // Si no se encuentra el artículo en el carrito de compras, regresar un mensaje
      // de error
      if (!rs.next()) {
        return Response.status(404).entity(j.toJson(new Error("El artículo no está en el carrito de compras"))).build();
      }

      // Obtener la cantidad del artículo en el carrito de compras
      int cantidadEnCarrito = rs.getInt("cantidad");

      // Eliminar el artículo de la tabla de carrito_compra
      query = "DELETE FROM carrito_compra WHERE id_articulo = ?";
      statement = conexion.prepareStatement(query);
      statement.setInt(1, idArticulo);
      statement.executeUpdate();

      // Devolver la cantidad del artículo a la tabla de artículos
      query = "UPDATE articulos SET cantidad = cantidad + ? WHERE id = ?";
      statement = conexion.prepareStatement(query);
      statement.setInt(1, cantidadEnCarrito);
      statement.setInt(2, idArticulo);
      statement.executeUpdate();

      conexion.commit();
      return Response.status(200).entity("Artículo eliminado del carrito de compras").build();

    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @POST
  @Path("eliminar_articulos")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response eliminarArticulos(String json) throws Exception {
    ParamEliminarArticulos p = (ParamEliminarArticulos) j.fromJson(json, ParamEliminarArticulos.class);
    List<Articulo> articulos = p.articulos;

    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);
      for (Articulo articulo : articulos) {
        String nombre = articulo.nombre;
        String descripcion = articulo.descripcion;
        // Buscar el articulo por nombre y descripcion
        String query = "SELECT id FROM articulos WHERE nombre = ? AND descripcion = ?";
        PreparedStatement statement = conexion.prepareStatement(query);
        statement.setString(1, nombre);
        statement.setString(2, descripcion);
        ResultSet rs = statement.executeQuery();

        // Si no se encuentra el articulo, regresar un mensaje de error
        if (!rs.next()) {
          return Response.status(404).entity(j.toJson(new Error("No se encontró el artículo solicitado"))).build();
        }


        // Obtener la cantidad del artículo en el carrito de compras
        int cantidadEnCarrito = articulo.cantidad;

        // Eliminar el articulo del carrito de compras
        int idArticulo = rs.getInt("id");
        query = "DELETE FROM carrito_compra WHERE id_articulo = ?";
        statement = conexion.prepareStatement(query);
        statement.setInt(1, idArticulo);
        statement.executeUpdate();

        // Regresar la cantidad del articulo eliminado a la tabla de articulos
        query = "UPDATE articulos SET cantidad = cantidad + ? WHERE id = ?";
        statement = conexion.prepareStatement(query);
        statement.setInt(1, cantidadEnCarrito);
        statement.setInt(2, idArticulo);
        statement.executeUpdate();
      }
      conexion.commit();
      return Response.status(200).entity("Artículos eliminados del carrito de compras").build();

    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @DELETE
  @Path("vaciar_carrito")
  @Produces(MediaType.APPLICATION_JSON)
  public Response vaciarCarrito() throws Exception {
    Connection conexion = pool.getConnection();
    try {
      conexion.setAutoCommit(false);
      // Obtener todos los registros del carrito de compras
      String query = "SELECT id_articulo, cantidad FROM carrito_compra";
      PreparedStatement statement = conexion.prepareStatement(query);
      ResultSet rs = statement.executeQuery();

      // Iterar sobre los registros y devolver la cantidad a la tabla de articulos
      while (rs.next()) {
        int idArticulo = rs.getInt("id_articulo");
        int cantidad = rs.getInt("cantidad");
        query = "UPDATE articulos SET cantidad = cantidad + ? WHERE id = ?";
        statement = conexion.prepareStatement(query);
        statement.setInt(1, cantidad);
        statement.setInt(2, idArticulo);
        statement.executeUpdate();
      }

      // Eliminar todos los registros del carrito de compras
      query = "DELETE FROM carrito_compra";
      statement = conexion.prepareStatement(query);
      statement.executeUpdate();

      conexion.commit();
      return Response.status(200).entity("El carrito de compras se ha vaciado correctamente").build();

    } catch (SQLException e) {
      // Error en la transacción
      try {
        conexion.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      String responseJson = "{\"message\": \"Error en la transacción: " + e.getMessage() + "\"}";
      return Response.status(500).entity(responseJson).build();
    } finally {
      // Cerrar la conexión a la BD
      try {
        conexion.setAutoCommit(true);
        conexion.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

}
